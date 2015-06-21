package ch.goodrick.brewcontrol;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.cxf.service.factory.ServiceConstructionException;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

public class BrewControlTest {

	@Test(expected = ServiceConstructionException.class)
	public void testMainGPIO() throws Exception {
		String[] argv = new String[1];
		argv[0] = "gpio";
		BrewControl.main(argv);
		fail();
	}

	@Test(expected = UnsatisfiedLinkError.class)
	public void testMainPIFACE() throws Exception {
		String[] argv = new String[1];
		argv[0] = "piface";
		BrewControl.main(argv);
		fail();
	}

	@Test(expected = UnknownParameterException.class)
	public void testMain() throws Exception {
		String[] argv = new String[1];
		argv[0] = "bla";
		BrewControl.main(argv);
	}

}
