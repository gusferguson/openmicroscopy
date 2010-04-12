/*
 *   $Id$
 *
 *   Copyright 2009 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.services.blitz.repo;

import ome.services.blitz.fire.Registry;
import ome.services.util.Executor;
import omero.ServerError;
import omero.model.OriginalFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import Ice.Current;

/**
 * Simple repository service to make the ${java.io.tmpdir} available at runtime.
 * This is primarily for testing (see blitz-config.xml to disable) of the
 * repository infrastructure, and will lead to a number of repository objects
 * being created in the database.
 * 
 * @since Beta4.1
 */
public class TemporaryRepositoryI extends AbstractRepositoryI {

    private final static Log log = LogFactory
            .getLog(TemporaryRepositoryI.class);

    public TemporaryRepositoryI(Ice.ObjectAdapter oa, Registry reg,
            Executor ex, String sessionUuid) {
        super(oa, reg, ex, sessionUuid, System.getProperty("java.io.tmpdir"));
    }

    /**
     * @DEV.TODO CACHING
     */
    public String getFilePath(final OriginalFile file, Current __current)
            throws ServerError {

        String url = getFileUrl(file);

        if (url == null || !url.equals(getRepoUuid())) {
            String msg = String.format("%s (in %s) "
                    + "does not belong to this repository: %s", file.getId()
                    .getValue(), url, getRepoUuid());

            throw new omero.ValidationException(null, null, msg);
        }

        return file.getPath().getValue();

    }
}