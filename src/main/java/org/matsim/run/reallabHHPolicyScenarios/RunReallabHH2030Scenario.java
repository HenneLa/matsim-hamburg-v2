/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.reallabHHPolicyScenarios;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.contrib.dvrp.run.Modal;
import org.matsim.contrib.dvrp.run.MultiModals;
import org.matsim.contrib.dynagent.run.DynActivityEngine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.PreplanningEngineQSimModule;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfigurator;
import org.matsim.run.HamburgExperimentalConfigGroup;
import org.matsim.run.RunDRTHamburgScenario;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/** This class is used to simulate 2 policy scenarios for the year 2030: <br> <ul>
 * <li>ReallabHH2030 scenario which consists of the following measurements</i> <br> <ul>
 * <li>DRT-Feeder to public transport - only allowed for intermodal trips - no point2point service <br>
 * <li>DRT-based car and bike services (migrated from deprecated sharing contrib) <br>
 * <li>Heavy and wide bike infrastructure improvement. this is modeled via ASC, based on stated preference data by DLR <br>
 * <li>Mobility budget: 2.5 EUR/day as incentive to abandon private cars <br>
 * </ul>
 * <li> Reallab2030HH plus scenario which adds a modified transit schedule to the above described scenario.
 * </ul>
 *
 * Note: The sharing contrib was removed in MATSim 2025. This class has been migrated to use DRT-based services.
 */
public class RunReallabHH2030Scenario {

	private static final Logger log = LogManager.getLogger(RunReallabHH2030Scenario.class);

	static final String CFG_REALLABHH2030_SCENARIO = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/hamburg/hamburg-v3/v3.0/input/reallab2030/hamburg-v3.0-10pct.config.reallabHH2030.xml";
	static final String CFG_REALLABHH2030_PLUS_SCENARIO = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/hamburg/hamburg-v3/v3.0/input/reallab2030plus/hamburg-v3.0-10pct.config.reallabHH2030-plus.xml";


	public static void main(String[] args) throws IOException {

		for (String arg : args) {
			log.info(arg);
		}

		if (args.length == 0) {
			args = new String[] {CFG_REALLABHH2030_SCENARIO};
		}

		Config config = prepareConfig(args);
		Scenario scenario = prepareScenario(config);

		Controler controler = prepareControler(scenario);

		//run the simulation
		controler.run();
	}

	public static Config prepareConfig(String[] args) {
		// Create config with DRT configuration
		Config config = RunDRTHamburgScenario.prepareConfig(args);

		// Important: adjust bike parameters first
		adjustBikeParameters(config);

		return config;
	}

	private static void adjustBikeParameters(Config config) {
		ScoringConfigGroup.ModeParams bikeParams = config.scoring().getModes().get(TransportMode.bike);

		if (bikeParams != null) {
			double ce_beta_lane = 1.08; //utility increase of a bike lane over no bike infrastructure
			double ce_beta_time = -0.258; // in utils/minute
			double ce_timeUtility_min_lane = ce_beta_lane / ce_beta_time * -1;

			double mtsm_total_time_costs_min = (bikeParams.getMarginalUtilityOfTraveling() - config.scoring().getPerforming_utils_hr()) / 60.;
			double mtsm_utility_lane = -mtsm_total_time_costs_min * ce_timeUtility_min_lane;

			bikeParams.setConstant(bikeParams.getConstant() + mtsm_utility_lane);
		}
	}

	public static Scenario prepareScenario(Config config) throws IOException {
		// Load and prepare scenario with DRT
		Scenario scenario = RunDRTHamburgScenario.prepareScenario(config);
		return scenario;
	}

	public static Controler prepareControler(Scenario scenario) {
		// Set up controler with DRT modules
		Controler controler = RunDRTHamburgScenario.prepareControler(scenario);

		// Configure QSim components for all DRT modes
		MultiModeDrtConfigGroup drtfg = MultiModeDrtConfigGroup.get(controler.getConfig());
		List<String> dvrpModes = drtfg.getModalElements().stream().map(Modal::getMode).collect(toList());
		controler.configureQSimComponents(qSimComponentConfigurator(dvrpModes));

		// Add mobility budget (monetary incentive to abandon car)
		Double mobilityBudget = ConfigUtils.addOrGetModule(scenario.getConfig(), HamburgExperimentalConfigGroup.class).getfixedDailyMobilityBudget();
		Preconditions.checkNotNull(mobilityBudget, "you need to specify fixedDailyMobilityBudget in " + HamburgExperimentalConfigGroup.class);
		Map<Id<Person>, Double> person2MobilityBudget = RunBaseCaseWithMobilityBudget.getPersonsEligibleForMobilityBudget2FixedValue(scenario, mobilityBudget);
		MobilityBudgetEventHandler mobilityBudgetHandler = new MobilityBudgetEventHandler(person2MobilityBudget);
		RunBaseCaseWithMobilityBudget.addMobilityBudgetHandler(controler, mobilityBudgetHandler);

		return controler;
	}

	private static QSimComponentsConfigurator qSimComponentConfigurator(List<String> dvrpModes) {
		return components -> {
			components.addNamedComponent(DynActivityEngine.COMPONENT_NAME);
			components.addNamedComponent(PreplanningEngineQSimModule.COMPONENT_NAME);

			// Activate all DvrpMode components
			MultiModals.requireAllModesUnique(dvrpModes);
			for (String m : dvrpModes) {
				components.addComponent(DvrpModes.mode(m));
			}
		};
	}
}
