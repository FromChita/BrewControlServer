package ch.goodrick.brewcontrol.mashing;

import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.goodrick.brewcontrol.actuator.Actuator;
import ch.goodrick.brewcontrol.actuator.FakeActuator;
import ch.goodrick.brewcontrol.sensor.SensorListener;
import ch.goodrick.brewcontrol.sensor.SensorThread;

/**
 * This class executes a rest i.e. heats up to the expected temperature and
 * waits for the defined time.
 * 
 * @author sebastian@goodrick.ch
 *
 */
public class RestExecuter implements Runnable, SensorListener {
	private final Rest rest;
	private final Actuator heater;
	private final SensorThread temperatureSensor;
	private final Logger log = LoggerFactory.getLogger(this.getClass());
	private final HashSet<RestStateChangeListener> listener = new HashSet<RestStateChangeListener>();
	private int timeIntervalInMS = 1000; // TODO move to config file
	private final double tolerance = 0.3; // tolerance in °C

	// first Integer: Delta °C in centidegrees, i.e. 10 = 1°C
	// second Integer: % of heating time
	private SortedMap<Integer, Integer> temperatureAdjust;

	public RestExecuter(Rest rest, Actuator heater, SensorThread temperatureSensor, RestStateChangeListener... listener) {
		this.rest = rest;
		this.heater = heater;
		this.temperatureSensor = temperatureSensor;
		addListener(listener);

		// adjust timing in simulation mode
		if (heater instanceof FakeActuator) {
			timeIntervalInMS = 5000;
		}

		temperatureAdjust = new TreeMap<Integer, Integer>(new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o2.compareTo(o1);
			}
		});

		// set up temperature adjustment
		// TODO move to config file
		temperatureAdjust.put(11, 100); // power!
		temperatureAdjust.put(10, 80); // reduce heater to 80% at 10
										// centiDegrees before
										// reaching rest temperature
		temperatureAdjust.put(5, 75); // reduce heater by 75% at .5°C before
										// reaching rest temperature
		temperatureAdjust.put(4, 50); // reduce heater by 50% at .4°C before
										// reaching rest temperature
		temperatureAdjust.put(3, 30); // reduce heater by 30% at .3°C before
										// reaching rest temperature
		temperatureAdjust.put(2, 20); // reduce heater by 20% at .2°C before
										// reaching rest temperature
		temperatureAdjust.put(1, 10); // reduce heater by 10% at .1°C before
										// reaching rest temperature
		temperatureAdjust.put(0, 0); // switch off heater on or above rest
										// temperature
	}

	@Override
	public void run() {
		log.info(rest.getName() + " is starting.");
		// check if we are below largest delta -.no need to have a thread
		// running
		// Double delta = (rest.getTemperature() -
		// temperatureSensor.getTemperature()) * 10;
		// if (delta > temperatureAdjust.lastKey()) {
		// // register listener and return
		// temperatureSensor.addListenerAbove(this, rest.getTemperature() -
		// temperatureAdjust.lastKey());
		// setStatus();
		// heater.on();
		// log.info("Terminating RestExecuter thread");
		// return;
		// } else {
		// temperatureSensor.clearThresholdListener();
		// }

		// keep running in three states
		while (rest.getState().equals(RestState.HEATING) || rest.getState().equals(RestState.ACTIVE) || rest.getState().equals(RestState.INACTIVE)) {

			try {
				setStatus();
				heat();
			} catch (InterruptedException e) {
				log.error("Heating adjustment was interrupted!");
			}
		}

		// notify listeners that rest is over
		notifyListeners(rest.getState());

		// keep up heating while status is WAITING_COMPLETE
		while (rest.getState().equals(RestState.WAITING_COMPLETE)) {
			try {
				heat();
			} catch (InterruptedException e) {
				log.error("Heating adjustment was interrupted!");
			}
		}
		notifyListeners(rest.getState());
		clearListeners();
		log.info("Rest " + rest.getName() + " has been finished.");
	}

	/**
	 * Switch on heater for a percentage of the given time
	 */
	private void heat() throws InterruptedException {
		Integer delta = new Double((rest.getTemperature() - temperatureSensor.getTemperature()) * 10).intValue();
		for (Integer rTemp : temperatureAdjust.keySet()) {
			if (delta > rTemp) {
				Integer percent = new Double(new Double(temperatureAdjust.get(rTemp)) / 100 * timeIntervalInMS).intValue();
				heater.off();
				Thread.sleep(timeIntervalInMS - percent);
				heater.on();
				Thread.sleep(percent);
				return;
			}
		}
		// we are above all limits, switch heater off and wait!
		heater.off();
		Thread.sleep(timeIntervalInMS);
	}

	/**
	 * Sets the status of the rest according to what is happening.
	 */
	private void setStatus() {
		// adjust the status of the rest
		if (rest.getState().equals(RestState.INACTIVE)) {
			// set status to heating
			rest.setState(RestState.HEATING);
			log.info(rest.getName() + " is now in state " + rest.getState());
		} else if (rest.getState().equals(RestState.HEATING) && temperatureSensor.getTemperature() >= (rest.getTemperature() - tolerance)) {
			// set status to active
			rest.setState(RestState.ACTIVE);
			log.info(rest.getName() + " is now in state " + rest.getState());
		} else if (rest.getState().equals(RestState.ACTIVE)
				&& (new GregorianCalendar().getTimeInMillis() - rest.getActive().getTimeInMillis()) / 1000 / 60 > rest.getDuration()) {
			// time is up :)
			if (rest.isContinueAutomatically()) {
				rest.setState(RestState.COMPLETED);
			} else {
				rest.setState(RestState.WAITING_COMPLETE);
			}
			log.info(rest.getName() + " is now in state " + rest.getState());
		}
	}

	/**
	 * Add a listener to be notified on every rest state change.
	 * 
	 * @param listener
	 *            the listener to be notified.
	 */
	private void addListener(RestStateChangeListener... listener) {
		for (RestStateChangeListener l : listener) {
			this.listener.add(l);
		}
	}

	private void notifyListeners(RestState state) {
		// notify all regular listeners
		for (RestStateChangeListener l : listener) {
			l.onStateChangedEvent(state);
		}
	}

	private void clearListeners() {
		listener.clear();
	}

	@Override
	public void onSensorEvent(Double value) {
		(new Thread(this)).start();
	}

}