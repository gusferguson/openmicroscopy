/*
 * ome.util.utests.CommandLineRunnerTest
 *
 *   Copyright 2006 University of Dundee. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.util.utests;

import org.testng.annotations.*;

import ome.util.tasks.Run;
import junit.framework.TestCase;

public class CommandLineRunnerTest extends TestCase {

    @Test
    public void testTooManyEquals() throws Exception {
        arrayFails(new String[] { "user=josh=moore" });
    }

    // ~ Helpers
    // =========================================================================

    protected void arrayFails(String[] array) {
        try {
            Run.main(array);
            fail("Should throw IllegalArgument");
        } catch (Exception e) {
            assertTrue(IllegalArgumentException.class.isAssignableFrom(e
                    .getClass()));
        }
    }

}