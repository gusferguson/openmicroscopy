/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.licenses.test;

import junit.framework.TestCase;
import ome.system.Login;
import ome.system.OmeroContext;
import omero.ServerError;
import omero.api.ServiceInterfacePrx;
import omero.licenses.ILicensePrx;
import omero.licenses.ILicensePrxHelper;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = { "client", "integration", "blitz" })
public class BlitzLicenseTest extends TestCase {

    OmeroContext context;
    Login rootLogin;
    omero.client ice, root;
    ILicensePrx licenseService;
    byte[] token;

    @Override
    @BeforeMethod
    protected void setUp() throws Exception {
        super.setUp();

        context = OmeroContext.getInstance("ome.client.test");
        rootLogin = (Login) context.getBean("rootLogin");
        ice = new omero.client();
        ice.createSession();
        root = new omero.client();
        root.createSession(rootLogin.getName(), rootLogin.getPassword());

        licenseService = licensePrx(ice);

    }

    @Override
    @AfterClass
    protected void tearDown() throws Exception {
        licensePrx(root).resetLicenses();
        super.tearDown();
    }

    @Test
    public void testAcquireLicenseAutomatically() throws Exception {
        // should be done for us.
        passes(true);
    }

    @Test
    public void testAcquireLicenseManually() throws Exception {
        passes(true);
        assertTrue(licenseService.releaseLicense(token));
        passes(false);
        token = licenseService.acquireLicense();
        passes(true);
    }

    @Test
    public void testReset() throws Exception {

        long totalA = licenseService.getTotalLicenseCount();
        long availA = licenseService.getAvailableLicenseCount();

        token = licenseService.acquireLicense();

        long totalB = licenseService.getTotalLicenseCount();
        long availB = licenseService.getAvailableLicenseCount();

        licensePrx(root).resetLicenses();

        long totalC = licenseService.getTotalLicenseCount();
        long availC = licenseService.getAvailableLicenseCount();

        assertTrue(totalA == totalB && totalB == totalC);
        assertTrue(availA < totalA && totalC == availC);
        assertTrue(availB == (availA - 1));

    }

    @Test(groups = { "ignore", "NYI" })
    public void testTimeouts() {
        fail("Not implemented.");
    }

    // // ~ Helpers
    // =========================================================================

    protected void passes(boolean shouldpass) throws Exception {
        try {
            ice.getServiceFactory().getConfigService().getServerTime();
            if (!shouldpass) {
                fail("Shoudn't have passed.");
            }
        } catch (Exception ex) {
            if (shouldpass) {
                throw ex;
            }
        }

    }

    private ILicensePrx licensePrx(omero.client sf) throws ServerError {
        ServiceInterfacePrx prx = sf.getServiceFactory().getByName(
                "omero.licenses.ILicense");
        ILicensePrx licenses = ILicensePrxHelper.uncheckedCast(prx);
        return licenses;
    }
}