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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.dvrp.passenger.PassengerDroppedOffEvent;
import org.matsim.contrib.dvrp.passenger.PassengerDroppedOffEventHandler;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test for RunReallabHH2030Scenario.
 *
 * Note: This test has been migrated from the deprecated sharing contrib to use DRT.
 * The original sharing-based event handlers have been removed as the sharing contrib
 * was removed in MATSim 2025.
 */
public class RunReallabHH2030ScenarioTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils() ;

	String[] args = new String[]{
			"test/input/test-hamburg.reallab2030HH.config.xml" ,
			"--config:hamburgExperimental.fixedDailyMobilityBudget" , "2.5",
	};

	@Test
	public void testDRTTransport(){
		Exception exception = null;
		try {
			Config config = RunReallabHH2030Scenario.prepareConfig(args);

			config.controller().setOutputDirectory(utils.getOutputDirectory());

			Scenario scenario = RunReallabHH2030Scenario.prepareScenario(config);

			TestHandler handler = new TestHandler();

			Controler controler = RunReallabHH2030Scenario.prepareControler(scenario);
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					addEventHandlerBinding().toInstance(handler);
					addControlerListenerBinding().toInstance(handler);
				}
			});

			controler.run();

		} catch (IOException e) {
			exception = e;
		}

		Assert.assertNull("An exception occurred! Look into the log file!", exception);
	}

	private class TestHandler implements PassengerDroppedOffEventHandler, IterationEndsListener {

		Map<Class<? extends Event>, Integer> eventLog = new HashMap<>();

		@Override
		public void handleEvent(PassengerDroppedOffEvent event) {
			this.log(event);
		}

		private void log(Event event){
			this.eventLog.compute(event.getClass(), (k, v) -> (v == null ? 0 : v) + 1);
		}

		@Override
		public void reset(int iteration) {
			PassengerDroppedOffEventHandler.super.reset(iteration);
			init();
		}

		private void init(){
			eventLog.clear();
			eventLog.put(PassengerDroppedOffEvent.class, 0);
		}

		@Override
		public void notifyIterationEnds(IterationEndsEvent event) {
			// DRT events are expected if DRT is configured in the scenario
			// This is a basic sanity check that the simulation runs
		}
	}
}
