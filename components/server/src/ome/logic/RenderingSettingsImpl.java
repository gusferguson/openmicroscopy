/*
 *   $Id$
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2009 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

package ome.logic;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.perf4j.StopWatch;
import org.perf4j.commonslog.CommonsLogStopWatch;
import org.springframework.transaction.annotation.Transactional;

import ome.annotations.NotNull;
import ome.annotations.RevisionDate;
import ome.annotations.RevisionNumber;
import ome.annotations.RolesAllowed;
import ome.api.IPixels;
import ome.api.IRenderingSettings;
import ome.api.ServiceInterface;
import ome.conditions.ResourceError;
import ome.conditions.ValidationException;
import ome.io.nio.OriginalFileMetadataProvider;
import ome.io.nio.PixelBuffer;
import ome.io.nio.PixelsService;
import ome.model.IObject;
import ome.model.acquisition.Filter;
import ome.model.acquisition.Laser;
import ome.model.acquisition.LightSource;
import ome.model.acquisition.TransmittanceRange;
import ome.model.containers.Dataset;
import ome.model.containers.Project;
import ome.model.core.Channel;
import ome.model.core.Image;
import ome.model.core.LogicalChannel;
import ome.model.core.Pixels;
import ome.model.display.ChannelBinding;
import ome.model.display.QuantumDef;
import ome.model.display.RenderingDef;
import ome.model.enums.Family;
import ome.model.enums.RenderingModel;
import ome.model.meta.Session;
import ome.model.screen.Screen;
import ome.model.screen.Plate;
import ome.model.stats.StatsInfo;
import ome.parameters.Parameters;
import ome.services.OmeroOriginalFileMetadataProvider;
import omeis.providers.re.ColorsFactory;
import omeis.providers.re.Renderer;
import omeis.providers.re.data.PlaneDef;
import omeis.providers.re.metadata.StatsFactory;
import omeis.providers.re.quantum.QuantumFactory;

/**
 * Implementation of the {@link IRenderingSettings} I/F.
 * 
 * @author Aleksandra Tarkowska <a
 *         href="mailto:a.tarkowska@dundee.ac.uk">a.tarkowska@dundee.ac.uk</a>
 * @version 3.0 <small> (<b>Internal version:</b> $Revision: 1696 $ $Date:
 *          2007/09/06 23:27:31 $) </small>
 * @since OME3.0
 */
@RevisionDate("$Date: 2007-09-06 14:29:18 +0100 (Thu, 06 Sep 2007) $")
@RevisionNumber("$Revision: 1792 $")
@Transactional
public class RenderingSettingsImpl extends AbstractLevel2Service implements
        IRenderingSettings, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -4383698215540637039L;
    
    /** The logger for this class. */
    private transient static Log log = 
        LogFactory.getLog(RenderingSettingsImpl.class);

    /** Reference to the service used to retrieve the pixels data. */
    protected transient PixelsService pixelsData;

    /** Reference to the service used to retrieve the pixels metadata. */
    protected transient IPixels pixelsMetadata;

    /**
     * Returns the Id of the currently logged in user.
     * @return See above.
     */
    private Long getCurrentUserId()
    {
        return getSecuritySystem().getEventContext().getCurrentUserId();
    }

    /**
     * Checks to see if a given class is a valid container.
     * @param klass IObject derived class to check for validity.
     * @throws IllegalArgumentException If the class <code>klass</code> is
     * an invalid container.
     */
    private void checkValidContainerClass(Class<? extends IObject> klass)
    {
    	if (!Project.class.equals(klass)
    		&& !Dataset.class.equals(klass)
            && !Image.class.equals(klass)
            && !Plate.class.equals(klass)
            && !Pixels.class.equals(klass) 
            && !Screen.class.equals(klass))
        	{
        		throw new IllegalArgumentException(
        			"Class parameter for resetDefaultsInSet() must be in " +
        			"{Project, Dataset, Image, Plate, Screen, Pixels}, not " + 
        				klass);
        	}	
    }
    
    /**
     * Updates a collection of Pixels from an arbitrary set of nodes.
     * @param pixels Collection to update.
     * @param klass Instance type of the objects for which <code>nodeIds</code>
     * represent a primary ID for.
     * @param nodeIds Set of node IDs to lookup Pixels against.
     */
    private void updatePixelsForNodes(List<Pixels> pixels, 
    		                          Class<? extends IObject> klass,
    		                          Set<Long> nodeIds)
    {
    	// Pre-process our list of potential containers. This will resolve down
    	// to a list of Pixels objects for us to work on.
    	if (Project.class.equals(klass))
    	{
    		for (Long projectId : nodeIds)
    		{
    			pixels.addAll(loadProjectPixels(projectId));
    		}
    	}
    	if (Dataset.class.equals(klass))
    	{
    		for (Long datasetId : nodeIds)
    		{
    			pixels.addAll(loadDatasetPixels(datasetId));
    		}
    	}
    	if (Plate.class.equals(klass))
    	{
    		for (Long plateId : nodeIds)
    		{
    			pixels.addAll(loadPlatePixels(plateId));
    		}
    	}
    	if (Screen.class.equals(klass))
    	{
    		for (Long screenId : nodeIds)
    		{
    			pixels.addAll(loadScreenPixels(screenId));
    		}
    	}
    	if (Image.class.equals(klass))
    	{
    		pixels.addAll(loadPixelsByImage(nodeIds));
    	}
    	if (Pixels.class.equals(klass))
    	{
    		pixels.addAll(loadPixels(nodeIds));
    	}
    }
    
    /**
     * Retrieves all Pixels by ID.
     * 
     * @param pixelsIds Pixels IDs to retrieve Pixels for.
     * @return List of Pixels with the given Pixels IDs.
     */
    private List<Pixels> loadPixels(Set<Long> pixelsIds)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadPixelsByImage");
		Parameters p = new Parameters();
		p.addIds(pixelsIds);
		String sql = "select pix from Pixels as pix " +
			"join fetch pix.image " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"where pix.id in (:ids)";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Retrieves all Pixels linked to a given set of Image IDs.
     * 
     * @param imageIds Image IDs to retrieve Pixels for.
     * @return List of Pixels associated with the given Image IDs.
     */
    private List<Pixels> loadPixelsByImage(Set<Long> imageIds)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadPixelsByImage");
		Parameters p = new Parameters();
		p.addIds(imageIds);
		String sql = "select pix from Pixels as pix " +
			"join fetch pix.image as i " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"where i.id in (:ids)";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Retrieves all Pixels associated with a Plate from the database.
     * @param plateId Plate ID to retrieve Pixels for.
     * @return List of Pixels associated with the Plate.
     */
    private List<Pixels> loadPlatePixels(Long plateId)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadPlatePixels");
		Parameters p = new Parameters();
		p.addId(plateId);
		String sql = "select pix from Pixels as pix " +
			"join fetch pix.image as i " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"left outer join i.wellSamples as s " +
			"left outer join s.well as w " +
			"left outer join w.plate as p " +
			"where p.id = :id";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Retrieves all Pixels associated with a Screen from the database.
     * @param plateId Plate ID to retrieve Pixels for.
     * @return List of Pixels associated with the Plate.
     */
    private List<Pixels> loadScreenPixels(Long plateId)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadScreenPixels");
		Parameters p = new Parameters();
		p.addId(plateId);
		String sql = "select pix from Pixels as pix " +
			"join fetch pix.image as i " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"left outer join i.wellSamples as s " +
			"left outer join s.well as w " +
			"left outer join w.plate as p " +
			"left outer join p.screenLinks as spl " +
			"left outer join spl.parent as s " +
			"where s.id = :id";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Retrieves all Pixels associated with a Dataset from the database.
     * 
     * @param datasetId Dataset ID to retrieve Pixels for.
     * @return List of Pixels associated with the Dataset.
     */
    private List<Pixels> loadDatasetPixels(Long datasetId)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadDatasetPixels");
		Parameters p = new Parameters();
		p.addId(datasetId);
    	String sql = "select pix from Pixels as pix " +
    		"join fetch pix.image as i " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"left outer join i.datasetLinks dil " +
			"left outer join dil.parent d " +
			"where d.id = :id";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Retrieves all Pixels associated with a Project from the database.
     * 
     * @param projectIds Project ID to retrieve Pixels for.
     * @return List of Pixels associated with the Project.
     */
    private List<Pixels> loadProjectPixels(Long projectIds)
    {
		StopWatch s1 = new CommonsLogStopWatch("omero.loadProjectPixels");
		Parameters p = new Parameters();
		p.addId(projectIds);
    	String sql = "select pix from Pixels as pix " +
    		"join fetch pix.image as i " +
			"join fetch pix.pixelsType " +
			"join fetch pix.channels as c " +
			"join fetch c.logicalChannel " +
			"left outer join i.datasetLinks dil " +
			"left outer join dil.parent as d " +
			"left outer join d.projectLinks as pdl " +
			"left outer join pdl.parent as p " +
			"where p.id = :id";
		List<Pixels> pixels = iQuery.findAllByQuery(sql, p);
		s1.stop();
		return pixels;
    }
    
    /**
     * Loads the logical channel to determine the color correctly.
     * 
     * @param id The id of the channel.
     * @return See above.
     */
    private LogicalChannel loadLogicalChannel(Long id)
    {
    	StopWatch s1 = new CommonsLogStopWatch("omero.loadLogicalChannel");
		Parameters p = new Parameters();
		p.addId(id);
    	StringBuilder sb = new StringBuilder();
    	sb.append("select channel from LogicalChannel as channel ");
    	
        sb.append("left outer join fetch channel.filterSet as filter ");
        sb.append("left outer join fetch filter.emFilter as ef ");
        sb.append("left outer join fetch filter.exFilter as exf ");
        sb.append("left outer join fetch ef.transmittanceRange as efTrans ");
        sb.append("left outer join fetch exf.transmittanceRange as exfTrans ");
        sb.append("left outer join fetch channel.secondaryEmissionFilter as " +
        		"emfilter ");
        sb.append("left outer join fetch emfilter.transmittanceRange as " +
        		"emTrans ");
        sb.append("left outer join fetch channel.secondaryExcitationFilter as " +
		"exfilter ");
        sb.append("left outer join fetch exfilter.transmittanceRange as " +
        		"exTrans ");
        sb.append("left outer join fetch channel.lightSourceSettings as lss ");
        sb.append("left outer join fetch lss.lightSource as ls ");
        //
        sb.append("where channel.id = :id");
        return (LogicalChannel) iQuery.findByQuery(sb.toString(), p);
    }
    
    /**
     * Retrieves all rendering settings associated with a given set of Pixels.
     * @param pixels List of Pixels to retrieve settings for.
     * @return A map of &lt;Pixels.Id,RenderingDef&gt; for the list of Pixels
     * given. 
     */
    private Map<Long, RenderingDef> loadRenderingSettings(List<Pixels> pixels)
    {
        return loadRenderingSettings(pixels, getCurrentUserId());
    }

    /**
     * Retrieves all rendering settings associated with a given set of Pixels.
     * @param pixels List of Pixels to retrieve settings for.
     * @param userId User ID of the owner of the settings to query for.
     * @return A map of &lt;Pixels.Id,RenderingDef&gt; for the list of Pixels
     * given. 
     */
    private Map<Long, RenderingDef> loadRenderingSettings(List<Pixels> pixels,
                                                          Long ownerId)
    {
        StopWatch s1 = new CommonsLogStopWatch(
                "omero.loadRenderingSettingsByUser");
        Set<Long> pixelsIds = new HashSet<Long>();
        for (Pixels p : pixels)
        {
            pixelsIds.add(p.getId());
        }
        Parameters p = new Parameters();
        p.addIds(pixelsIds);
        p.addId(ownerId);
        String sql = PixelsImpl.RENDERING_DEF_QUERY_PREFIX +
            "rdef.pixels.id in (:ids) and " +
            "rdef.details.owner.id = :id";
        Map<Long, RenderingDef> settingsMap = new HashMap<Long, RenderingDef>();
        List<RenderingDef> settingsList = iQuery.findAllByQuery(sql, p);
        for (RenderingDef settings : settingsList)
        {
            settingsMap.put(settings.getPixels().getId(), settings);
        }
        s1.stop();
        return settingsMap;
    }

    /**
     * Retrieves all rendering settings associated with a given set of Pixels
     * that belong to the owner of each Pixels set.
     * @param pixels List of Pixels to retrieve settings for.
     * @return A map of &lt;Pixels.Id,RenderingDef&gt; for the list of Pixels
     * given. 
     */
    private Map<Long, RenderingDef> loadRenderingSettingsByOwner(
            List<Pixels> pixels)
    {
        StopWatch s1 = new CommonsLogStopWatch(
                "omero.loadRenderingSettingsByOwner");
        Set<Long> pixelsIds = new HashSet<Long>();
        for (Pixels p : pixels)
        {
            pixelsIds.add(p.getId());
        }
        Parameters p = new Parameters();
        p.addIds(pixelsIds);
        String sql = PixelsImpl.RENDERING_DEF_QUERY_PREFIX +
            "rdef.pixels.id in (:ids) and " +
            "rdef.details.owner.id = ref.pixels.details.owner.id";
        Map<Long, RenderingDef> settingsMap = new HashMap<Long, RenderingDef>();
        List<RenderingDef> settingsList = iQuery.findAllByQuery(sql, p);
        for (RenderingDef settings : settingsList)
        {
            settingsMap.put(settings.getPixels().getId(), settings);
        }
        s1.stop();
        return settingsMap;
    }

    /**
     * Resets a specific set of rendering settings back to those that are 
     * specified by the rendering engine intelligent <i>pretty good image 
     * (PG)</i> logic and a given pixels set.
     * 
     * @param pixels The pixels object whose rendering settings are to be reset.
     * @param settings The rendering settings which are to be reset.
     * @param save Whether or not the rendering settings should be saved.
     * @param computeStats Pass <code>true</code> to compute the stats 
     * 					   determining the input interval, <code>false</code>
     *                     otherwise.
     * @param families The valid rendering family enumerations.
     * @param renderingModels The valid rendering model enumerations.
     * @return See above.
     */
    private RenderingDef resetDefaults(RenderingDef settings, Pixels pixels,
                                       boolean save, boolean computeStats,
                                       List<Family> families, 
                                       List<RenderingModel> renderingModels)
    {
    	// Handle the case where we have no rendering settings so that we can
    	// reset "pretty good image" or "original" (channel minimum and
    	// maximum) when they don't exist.
        if (settings == null)
        {
        	settings = createNewRenderingDef(pixels);
        }
        
        QuantumFactory quantumFactory = new QuantumFactory(families);
        try
        {
        	PixelBuffer buffer = null;
        	if (computeStats)
        	{
        		OriginalFileMetadataProvider metadataProvider =
        			new OmeroOriginalFileMetadataProvider(iQuery);
        		buffer = 
        			pixelsData.getPixelBuffer(pixels, metadataProvider, false);
        	}
            resetDefaults(settings, pixels, quantumFactory,
                    renderingModels, buffer, computeStats);
            if (buffer != null)
            {
            	buffer.close();
            }
            
            // Increment the version of the rendering settings so that we 
            // can have some notification that either the RenderingDef 
            // object itself or one of its children in the object graph has 
            // been updated. FIXME: This should be implemented using 
            // IUpdate.touch() or similar once that functionality exists.
            settings.setVersion(settings.getVersion() + 1);
            
            if (save)
            {
            	log.info("Saving settings: " + settings);
                pixelsMetadata.saveRndSettings(settings);
            }
            return settings;
        } 
        catch (IOException e)
        {
            log.debug("An I/O error occurred while attempting to reset " +
                      "rendering settings " + settings + " for pixels set " + 
                      pixels, e);
            throw new ResourceError(
                    e.getMessage() + " Please check server log.");
        }
    }
    
    /**
     * Resets a rendering definition to its predefined defaults.
     * 
     * @param def The rendering definition to reset.
     * @param pixels The pixels set to reset the definition based upon.
     * @param quantumFactory A populated quantum factory.
     * @param renderingModels An enumerated list of all rendering models.
     * @param buffer A pixel buffer which maps to the <i>planeDef</i>.
     * @param computeStats Pass <code>true</code> to compute the stats,
     * 			           <code>false</code> otherwise
     */
    private void resetDefaults(RenderingDef def, Pixels pixels,
            QuantumFactory quantumFactory, List<RenderingModel> renderingModels,
            PixelBuffer buffer, boolean computeStats) {
        // The default rendering definition settings
        def.setDefaultZ(pixels.getSizeZ() / 2);
        def.setDefaultT(0);
    
        // Set the rendering model to RGB if there is more than one channel,
        // otherwise set it to greyscale.
        RenderingModel defaultModel = null;
        int sizeC = pixels.getSizeC();
        if (sizeC > 1 && sizeC < Renderer.MAX_CHANNELS) {
            for (RenderingModel model : renderingModels)
            {
                if (model.getValue().equals(Renderer.MODEL_RGB))
                    defaultModel = model;
            }
        } else {
            for (RenderingModel model : renderingModels)
            {
                if (model.getValue().equals(Renderer.MODEL_GREYSCALE))
                    defaultModel = model;
            }
        }
        if (defaultModel == null)
        {
            throw new IllegalArgumentException(
                "Unable to find default rendering model in enumerated list.");
        }
        def.setModel(defaultModel);
    
        // Quantization settings
        QuantumDef quantumDef = def.getQuantization();
        quantumDef.setCdStart(0);
        quantumDef.setCdEnd(QuantumFactory.DEPTH_8BIT);
        quantumDef.setBitResolution(QuantumFactory.DEPTH_8BIT);
        def.setQuantization(quantumDef);
    
        // Reset the channel bindings
        resetChannelBindings(def, pixels, quantumFactory, buffer, computeStats);
    }
    
    /**
     * Performs the logic specified by {@link #resetDefaultsInSet()} and
     * {@link #setOriginalSettingsInSet()}.
     */
    private <T extends IObject> Set<Long> resetDefaultsInSet(
            Class<T> klass, Set<Long> nodeIds, boolean computeStats)
    {
    	checkValidContainerClass(klass);
    	
    	// Load our dependencies for rendering settings manipulation
    	StopWatch s1 = new CommonsLogStopWatch("omero.resetDefaultsInSet");
        List<Family> families = pixelsMetadata.getAllEnumerations(Family.class);
        List<RenderingModel> renderingModels = 
            pixelsMetadata.getAllEnumerations(RenderingModel.class);
    	// Pre-process our list of potential containers. This will resolve down
    	// to a list of Pixels objects for us to work on.
    	List<Pixels> pixels = new ArrayList<Pixels>();
    	updatePixelsForNodes(pixels, klass, nodeIds);
    	
    	// Perform the actual work of resetting rendering settings, collecting
    	// the settings that need to be saved and saving the newly modified or
    	// created rendering settings in the database.
    	Set<Long> imageIds = new HashSet<Long>();
    	List<RenderingDef> toSave = new ArrayList<RenderingDef>(pixels.size());
    	Map<Long, RenderingDef> settingsMap = loadRenderingSettings(pixels);
    	RenderingDef settings;
    	for (Pixels p : pixels)
    	{
    		settings = settingsMap.get(p.getId());
    		if (settings == null)
    		{
    			settings = createNewRenderingDef(p);
    		}
    		try {
    			 toSave.add(resetDefaults(settings, p, false, computeStats,
    					 		families, renderingModels));
    			 imageIds.add(p.getImage().getId());
			} catch (Exception e) {
				//Exception has already been written to log file.
			}
    	}
        StopWatch s2 = new CommonsLogStopWatch(
			"omero.resetDefaultsInSet.saveAndReturn");
        RenderingDef[] toSaveArray = 
        	toSave.toArray(new RenderingDef[toSave.size()]);
        iUpdate.saveAndReturnArray(toSaveArray);
        s2.stop();
        s1.stop();
    	return imageIds;
    }
    
    /**
     * Returns the cut in value if available.
     * 
     * @param f The filter to handle.
     * @return See above.
     */
    private String getValueFromFilter(Filter f)
    {
    	if (f == null) return null;
    	TransmittanceRange transmittance = f.getTransmittanceRange();
    	if (transmittance == null) return null;
    	return  ""+transmittance.getCutIn();
    }
    
    /**
     * Determines the name of the channel if possible.
     * 
     * @param lc The channel to handle.
     * @return See above.
     */
    private String getChannelName(LogicalChannel lc)
    {
    	String name;
    	Integer value = lc.getEmissionWave();
    	if (value != null) return ""+value.intValue();
    	if (lc.getFilterSet() != null) {
    		Filter f = lc.getFilterSet().getEmFilter();
    		name  = getValueFromFilter(f);
    		if (name != null) return name;
    	}
    	name = getValueFromFilter(lc.getSecondaryEmissionFilter());
    	if (name != null) return name;
    	//Laser
    	if (lc.getLightSourceSettings() != null) {
    		LightSource src = lc.getLightSourceSettings().getLightSource();
    		if (src instanceof Laser) {
    			Laser laser = (Laser) src;
    			value = laser.getWavelength();
    			if (value != null) return ""+value.intValue();
    		}
    	}
    	value = lc.getExcitationWave();
    	if (value != null) return ""+value.intValue();
    	if (lc.getFilterSet() != null) {
    		Filter f = lc.getFilterSet().getExFilter();
    		name  = getValueFromFilter(f);
    		if (name != null) return name;
    	}
    	return getValueFromFilter(lc.getSecondaryExcitationFilter());
    }
    
    /**
     * Returns the original color if set at import or <code>null</code>.
     * if no color was set.
     * 
     * @param channel The channel to handle.
     * @return See above.
     */
    private int[] hasOriginalColor(Channel channel)
    {
    	Integer red = channel.getRed();
        Integer green = channel.getGreen();
        Integer blue = channel.getBlue();
        Integer alpha = channel.getAlpha();
        if (red != null && green != null && blue != null && alpha != null)
        	return new int[] { red, green, blue, alpha };
        return null;
    }
    
    /**
     * Resets the channel bindings for the current active pixels set.
     * 
     * @param def
     *            the rendering definition to link to.
     * @param pixels
     *            the pixels set to reset the bindings based upon.
     * @param quantumFactory
     *            a populated quantum factory.
     * @param buffer
     *            a pixel buffer which maps to the <i>planeDef</i>.
     * @param computeStats 
     * 			  Pass <code>true</code> to compute the stats,
     * 			  <code>false</code> otherwise
     */
    private void resetChannelBindings(RenderingDef def, Pixels pixels,
            QuantumFactory quantumFactory, PixelBuffer buffer, boolean
            computeStats) {
        // The actual channel bindings we are returning
        List<ChannelBinding> 
        	channelBindings = def.<ChannelBinding>collectWaveRendering(null);
    
        // Default plane definition for our rendering definition
        PlaneDef planeDef = getDefaultPlaneDef(def);
    
        int i = 0;
        ChannelBinding channelBinding;
        LogicalChannel lc;
        Family family;
        int[] defaultColor;
        int n = channelBindings.size();
        Map<ChannelBinding, Boolean> m = new HashMap<ChannelBinding, Boolean>();
        List<Boolean> values = new ArrayList<Boolean>();
        boolean v;
        String name;
        List<LogicalChannel> toUpdate = new ArrayList<LogicalChannel>();
        
        for (Channel channel : pixels.<Channel>collectChannels(null)) {
            family = quantumFactory.getFamily(QuantumFactory.LINEAR);
    
            channelBinding = channelBindings.get(i);
            channelBinding.setFamily(family);
            channelBinding.setCoefficient(new Double(1));
    
            // If we have more than one channel set each of the first three
            // active, otherwise only activate the first.
            channelBinding.setActive(i < 3);

            
            // Handle updating or recreating a color for this channel.
            defaultColor = hasOriginalColor(channel);
            if (defaultColor == null) {
            	lc = channel.getLogicalChannel();
                if (lc != null) lc = loadLogicalChannel(lc.getId());
                v = ColorsFactory.hasEmissionData(lc);
                //Update the name of the channel if no name, to be moved.
                /*
                name = lc.getName();
                if (name == null || name.trim().length() == 0) {
                	name = getChannelName(lc);
                	if (name != null) {
                		lc.setName(name);
                		toUpdate.add(lc);
                	}
                }
                
                if (!v) values.add(v);
                m.put(channelBinding, v);
                */
                defaultColor = ColorsFactory.getColor(i, channel, lc);
            } 
            channelBinding.setRed(defaultColor[ColorsFactory.RED_INDEX]);
            channelBinding.setGreen(defaultColor[ColorsFactory.GREEN_INDEX]);
            channelBinding.setBlue(defaultColor[ColorsFactory.BLUE_INDEX]);
            channelBinding.setAlpha(defaultColor[ColorsFactory.ALPHA_INDEX]);

            channelBinding.setNoiseReduction(false);
            i++;
        }
        if (values.size() > 0 && values.size() != m.size()) {
        	Iterator<ChannelBinding> k = m.keySet().iterator();
            while (k.hasNext()) {
    			channelBinding = k.next();
    			if (!m.get(channelBinding)) {
    				defaultColor = ColorsFactory.newWhiteColor();
    				channelBinding.setRed(
    						defaultColor[ColorsFactory.RED_INDEX]);
    	            channelBinding.setGreen(
    	            		defaultColor[ColorsFactory.GREEN_INDEX]);
    	            channelBinding.setBlue(
    	            		defaultColor[ColorsFactory.BLUE_INDEX]);
    	            channelBinding.setAlpha(
    	            		defaultColor[ColorsFactory.ALPHA_INDEX]);
    			}
    		}
        }
        // Set the input start and input end for each channel binding based upon
        // the computation of the pixels set's location statistics.
        if (computeStats)
        	computeLocationStats(pixels, channelBindings, planeDef, buffer);
        else {
        	StatsInfo stats;
            for (int w = 0; w < pixels.sizeOfChannels(); w++) {
                // FIXME: This is where we need to have the ChannelBinding -->
                // Channel linkage. Without it, we have to assume that the order in
                // which the channel bindings was created matches up with the order
                // of the channels linked to the pixels set.
            	channelBinding = channelBindings.get(w);
            	stats = pixels.getChannel(w).getStatsInfo();
            	if (stats == null)
            		throw new ResourceError("Pixels set is missing statistics" +
            				" for channel '"+ w +"'. This suggests an image " +
            		"import error or failed image import.");
            	channelBinding.setInputStart(stats.getGlobalMin().doubleValue());
            	channelBinding.setInputEnd(stats.getGlobalMax().doubleValue());
            }
        }
        
        //update the value.
        if (toUpdate.size() > 0) {
        	 StopWatch s1 = new CommonsLogStopWatch(
     		"omero.resetChannelBindings.saveAndReturn");
     	    LogicalChannel[] toSaveArray = 
     	    	toUpdate.toArray(new LogicalChannel[toUpdate.size()]);
     	    iUpdate.saveAndReturnArray(toSaveArray);
     	    s1.stop();
        }
    }
   
    /**
     * Computes the location statistics for a set of rendering settings.
     * 
     * @param pixels	The pixels set.
     * @param cbs		The collection of settings corresponding to channel.
     * @param planeDef	The 2D-plane. Mustn't be <code>null</code>
     * @param buf		The buffer.
     */
    private void computeLocationStats(Pixels pixels,
            List<ChannelBinding> cbs, PlaneDef planeDef, PixelBuffer buf) {
        if (planeDef == null) {
            throw new NullPointerException("No plane definition.");
        }
        StatsFactory sf = new StatsFactory();
        ChannelBinding cb;
        for (int w = 0; w < pixels.sizeOfChannels(); w++) {
            // FIXME: This is where we need to have the ChannelBinding -->
            // Channel linkage. Without it, we have to assume that the order in
            // which the channel bindings was created matches up with the order
            // of the channels linked to the pixels set.
            cb = cbs.get(w);
            sf.computeLocationStats(pixels, buf, planeDef, w);
            cb.setNoiseReduction(sf.isNoiseReduction());
            cb.setInputStart(new Double(sf.getInputStart()));
            cb.setInputEnd(new Double(sf.getInputEnd()));
        }
    }
    
    /**
     * Creates the default plane definition to use for generation of the very
     * first image displayed by <i>2D</i> viewers based upon a rendering
     * definition.
     * 
     * @param renderingDef
     *            the rendering definition to base the plane definition upon.
     * @return The default <i>XY</i>-plane for the <i>renderingDef</i>.
     */
    private PlaneDef getDefaultPlaneDef(RenderingDef renderingDef) {
        PlaneDef pd = new PlaneDef(PlaneDef.XY, renderingDef.getDefaultT());
        pd.setZ(renderingDef.getDefaultZ());
        return pd;
    }

    /**
     * Creates new channel bindings for each channel in the pixels set.
     * 
     * @param p
     *            the pixels set to create channel bindings based upon.
     * @return a new set of blank channel bindings.
     */
    private List<ChannelBinding> createNewChannelBindings(Pixels p)
    {
        List<ChannelBinding> cbs = new ArrayList<ChannelBinding>();
        ChannelBinding binding;
        for (int i = 0; i < p.getSizeC(); i++) {
            binding = new ChannelBinding();
            cbs.add(binding);
        }
        return cbs;
    }
    
    /**
     * Applies rendering settings from a source set of pixels and settings to
     * a destination set of pixels and settings.
     * @param pixelsFrom Source pixels object.
     * @param pixelsTo Source renderings settings.
     * @param settingsFrom Source rendering settings.
     * @param settingsTo Destination rendering settings. If <code>null</code>
     * these will be created on the fly.
     * @return The rendering settings modified or created. It is up to the
     * caller to save these settings.
     */
    private RenderingDef applySettings(Pixels pixelsFrom, Pixels pixelsTo,
                                       RenderingDef settingsFrom,
                                       RenderingDef settingsTo)
    {
        // Sanity checks
        log.debug(String.format(
                "Applying settings. From %s to %s and from %s to %s",
                pixelsFrom, pixelsTo, settingsFrom, settingsTo));
        boolean b = sanityCheckPixels(pixelsFrom, pixelsTo);
        if (!b)
        {
            return null;
        }
        if (settingsFrom == null)
        {
            return null;
        }
        if (settingsTo == null)
        {
        	settingsTo = createNewRenderingDef(pixelsTo);
        }
        
        int z = settingsFrom.getDefaultZ();
        if (z < pixelsTo.getSizeZ())
        	settingsTo.setDefaultZ(z);
        int t = settingsFrom.getDefaultT();
        if (t < pixelsTo.getSizeT())
        	settingsTo.setDefaultT(t);
        settingsTo.setModel(settingsFrom.getModel());
        
        QuantumDef qDefFrom = settingsFrom.getQuantization();
        QuantumDef qDefTo = settingsTo.getQuantization();

        qDefTo.setBitResolution(qDefFrom.getBitResolution());
        qDefTo.setCdEnd(qDefFrom.getCdEnd());
        qDefTo.setCdStart(qDefFrom.getCdStart());

        Iterator<ChannelBinding> i = settingsFrom.iterateWaveRendering();
        Iterator<ChannelBinding> iTo = settingsTo.iterateWaveRendering();
        ChannelBinding binding, bindingTo;
        while (i.hasNext())
        {
            binding = i.next();
            bindingTo = iTo.next();

            // channel on or off
            bindingTo.setActive(binding.getActive());
            // mapping coefficient
            bindingTo.setCoefficient(binding.getCoefficient());
            // type of map used
            bindingTo.setFamily(binding.getFamily());
            // lower bound of the pixels intensity interval
            bindingTo.setInputStart(binding.getInputStart());
            // upper bound of the pixels intensity interval
            bindingTo.setInputEnd(binding.getInputEnd());
            // turn on or off the noise reduction algo.
            bindingTo.setNoiseReduction(binding.getNoiseReduction());
            // color used
            bindingTo.setAlpha(binding.getAlpha());
            bindingTo.setBlue(binding.getBlue());
            bindingTo.setGreen(binding.getGreen());
            bindingTo.setRed(binding.getRed());
        }
        
        // Increment the version of the rendering settings so that we 
        // can have some notification that either the RenderingDef 
        // object itself or one of its children in the object graph has 
        // been updated. FIXME: This should be implemented using 
        // IUpdate.touch() or similar once that functionality exists.
        settingsTo.setVersion(settingsTo.getVersion() + 1);
        return settingsTo;
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#sanityCheckPixels(Pixels, Pixels)
     */
    public boolean sanityCheckPixels(Pixels pFrom, Pixels pTo) {
        if (pTo == null || pFrom == null)
            return false;
        String vFrom = pFrom.getPixelsType().getValue();
        String vTo = pTo.getPixelsType().getValue();
        if (!vFrom.equals(vTo))
            return false;
        if (pFrom.getSizeC().compareTo(pTo.getSizeC()) != 0)
            return false;
        Iterator<Channel> i = pFrom.iterateChannels();
        Channel c;
        List<Integer> wavelengths = new ArrayList<Integer>(pFrom
                .sizeOfChannels());
        // Problem no access to channel index.
        LogicalChannel lc;
        while (i.hasNext()) {
            c = i.next();
            lc = c.getLogicalChannel();
            if (lc != null)
                wavelengths.add(lc.getEmissionWave());
        }
        i = pTo.iterateChannels();
        int r = 0;
        while (i.hasNext()) {
            c = i.next();
            lc = c.getLogicalChannel();
            if (lc != null && wavelengths.contains(lc.getEmissionWave()))
                r++;
        }
        if (r != wavelengths.size())
            return false;
        return true;
    }

    /**
     * Sets injector. For use during configuration. Can only be called once.
     * 
     * @param metaService
     *            The value to set.
     */
    public void setPixelsMetadata(IPixels metaService) {
        getBeanHelper().throwIfAlreadySet(this.pixelsMetadata, metaService);
        pixelsMetadata = metaService;
    }

    /**
     * Sets injector. For use during configuration. Can only be called once.
     * 
     * @param dataService
     *            The value to set.
     */
    public void setPixelsData(PixelsService dataService) {
        getBeanHelper().throwIfAlreadySet(this.pixelsData, dataService);
        pixelsData = dataService;
    }

    /**
     * Returns the interface this implementation is for.
     * @see AbstractLevel2Service#getServiceInterface()
     */
    public Class<? extends ServiceInterface> getServiceInterface() {
        return IRenderingSettings.class;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#applySettingsToSet(long, Class, Set)
     */
    @RolesAllowed("user")
    public <T extends IObject> Map<Boolean, List<Long>> applySettingsToSet(
    		long from, Class<T> klass, Set<Long> nodeIds) {
    	checkValidContainerClass(klass);
    	
    	// Load our dependencies for rendering settings manipulation
    	StopWatch s1 = new CommonsLogStopWatch("omero.applySettingsToSet");
    	nodeIds.add(from);
    	// Pre-process our list of potential containers. This will resolve down
    	// to a list of Pixels objects for us to work on.
    	List<Pixels> pixels = new ArrayList<Pixels>();
    	updatePixelsForNodes(pixels, klass, nodeIds);
    	Pixels pixelsFrom = null;
    	
    	for (Pixels p : pixels)
    	{
    		if (p.getId() == from)
    		{
    			pixelsFrom = p;
    		}
    	}
    	/*
    	if (pixelsFrom == null)
    	{
    		throw new ValidationException("No pixels set with ID: " + from);
    	}
    	*/
    	
    	// Perform the actual work of copying rendering settings, collecting
    	// the settings that need to be saved and saving the newly modified or
    	// created rendering settings in the database.
    	List<Long> toReturnTrue = new ArrayList<Long>();
    	List<Long> toReturnFalse = new ArrayList<Long>();
    	List<RenderingDef> toSave = new ArrayList<RenderingDef>(pixels.size());
    	Map<Long, RenderingDef> settingsMap = loadRenderingSettings(pixels);
    	RenderingDef settingsFrom = settingsMap.get(from);
    	if (pixelsFrom != null) {
    		pixels.remove(pixelsFrom);
    		toReturnTrue.add(pixelsFrom.getImage().getId());
    	}
    	else {
    		//load pixels from
    		Set<Long> ids = new HashSet<Long>();
    		ids.add(from);
    		List<Pixels> l = loadPixels(ids);
    		if (l.size() != 1) 
    			throw new ValidationException("No pixels set with ID: " + from);
    		pixelsFrom = l.get(0);
    		if (settingsFrom == null) {
    			List<Pixels> list = new ArrayList<Pixels>(1);
    			list.add(pixelsFrom);
    			Map<Long, RenderingDef> map = loadRenderingSettings(list);
            	settingsFrom = map.get(from);
    		}
    	}
    		
    	RenderingDef settingsTo;
    	for (Pixels p : pixels)
    	{
    		settingsTo = settingsMap.get(p.getId());
            settingsTo = applySettings(pixelsFrom, p, settingsFrom, settingsTo);
            if (settingsTo == null)
            {
            	toReturnFalse.add(p.getImage().getId());
            }
            else
            {
            	toSave.add(settingsTo);
            	toReturnTrue.add(p.getImage().getId());
            }
    	}
        StopWatch s2 = new CommonsLogStopWatch(
			"omero.applySettingsToSet.saveAndReturn");
        RenderingDef[] toSaveArray = 
        	toSave.toArray(new RenderingDef[toSave.size()]);
        iUpdate.saveAndReturnArray(toSaveArray);
        s2.stop();
        s1.stop();
    	Map<Boolean, List<Long>> toReturn = new HashMap<Boolean, List<Long>>();
    	toReturn.put(Boolean.TRUE, toReturnTrue);
    	toReturn.put(Boolean.FALSE, toReturnFalse);
        return toReturn;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F. 
     * @see IRenderingSettings#applySettingsToProject(long, long)
     */
    @RolesAllowed("user")
    public Map<Boolean, List<Long>> applySettingsToProject(long from, long to)
    {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	return applySettingsToSet(from, Project.class, nodeIds);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#applySettingsToDataset(long, long)
     */
    @RolesAllowed("user")
    public Map<Boolean, List<Long>> applySettingsToDataset(long from, long to)
    {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	return applySettingsToSet(from, Dataset.class, nodeIds);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#applySettingsToImage(long, long)
     */
    @RolesAllowed("user")
    public boolean applySettingsToImage(long from, long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	Map<Boolean, List<Long>> returnValue = 
    		applySettingsToSet(from, Image.class, nodeIds);
    	if (returnValue.get(Boolean.TRUE).contains(to))
    	{
    		return true;
    	}
    	return false;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#applySettingsToImages(long, List)
     */
    @RolesAllowed("user")
    public Map<Boolean, List<Long>> applySettingsToImages(long from, 
    		List<Long> nodeIds) {
    	Set<Long> nodeIdSet = new HashSet<Long>(nodeIds);
    	return applySettingsToSet(from, Image.class, nodeIdSet);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#applySettingsToPixels(long, long)
     */
    @RolesAllowed("user")
    public boolean applySettingsToPixels(long from, long to)
    {
        Pixels pixelsFrom = pixelsMetadata.retrievePixDescription(from);
        Pixels pixelsTo = pixelsMetadata.retrievePixDescription(to);
        List<Pixels> pixelsList = new ArrayList<Pixels>(2);
        pixelsList.add(pixelsFrom);
        pixelsList.add(pixelsTo);
        Map<Long, RenderingDef> settingsMap = loadRenderingSettings(pixelsList);
        RenderingDef settingsFrom = settingsMap.get(from);
        RenderingDef settingsTo = settingsMap.get(to);
        settingsTo = applySettings(pixelsFrom, pixelsTo,
        		                   settingsFrom, settingsTo);
        if (settingsTo == null)
        {
        	return false;
        }
        iUpdate.saveObject(settingsTo);
        return true;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#getRenderingSettings(long)
     */
    @RolesAllowed("user")
    public RenderingDef getRenderingSettings(long pixelsId) {
        return pixelsMetadata.retrieveRndSettings(pixelsId);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#createNewRenderingDef(Pixels)
     */
    public RenderingDef createNewRenderingDef(@NotNull Pixels pixels) {
    	if (pixels == null) return null;
        RenderingDef r = new RenderingDef();
        //The default rendering definition settings
        r.setDefaultZ(pixels.getSizeZ() / 2);
        r.setDefaultT(0);
        r.setQuantization(new QuantumDef());

        List<ChannelBinding> list = createNewChannelBindings(pixels);
        r.clearWaveRendering();
        for (ChannelBinding channelBinding : list) {
            r.addChannelBinding(channelBinding);
        }
        // Unload the pixels object to avoid transactional headaches
        Pixels unloadedPixels = new Pixels();
        unloadedPixels.setId(pixels.getId());
        unloadedPixels.unload();
        r.setPixels(unloadedPixels);
        return r;
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#resetDefaults(RenderingDef, Pixels)
     */
    @RolesAllowed("user")
    public void resetDefaults(RenderingDef def, Pixels pixels) {
        List<Family> families = pixelsMetadata.getAllEnumerations(Family.class);
        List<RenderingModel> renderingModels = 
            pixelsMetadata.getAllEnumerations(RenderingModel.class);
        resetDefaults(def, pixels, true, true, families, renderingModels);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F
     * 
     * @see IRenderingSettings#resetDefaultsNoSave(RenderingDef, Pixels)
     */
    @RolesAllowed("user")
    public RenderingDef resetDefaultsNoSave(RenderingDef def, Pixels pixels) {
        List<Family> families = pixelsMetadata.getAllEnumerations(Family.class);
        List<RenderingModel> renderingModels = 
            pixelsMetadata.getAllEnumerations(RenderingModel.class);
        return resetDefaults(def, pixels, false, true, families, 
        		             renderingModels);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#resetDefaultsInImage(long)
     */
    @RolesAllowed("user")
    public void resetDefaultsInImage(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	resetDefaultsInSet(Image.class, nodeIds);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#resetDefaultsForPixels(long)
     */
    @RolesAllowed("user")
    public void resetDefaultsForPixels(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	resetDefaultsInSet(Pixels.class, nodeIds);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * 
     * @see IRenderingSettings#resetDefaultsInDataset(long)
     */
    @RolesAllowed("user")
    public Set<Long> resetDefaultsInDataset(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	return resetDefaultsInSet(Dataset.class, nodeIds);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F. 
     * @see IRenderingSettings#resetDefaultsInSet(Class, Set)
     */
    @RolesAllowed("user")
    public <T extends IObject> Set<Long> resetDefaultsInSet(Class<T> klass,
    		                                                Set<Long> nodeIds)
    {
    	return resetDefaultsInSet(klass, nodeIds, true);
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F. 
     * @see IRenderingSettings#resetDefaultsInSet(Class, Set)
     */
    @RolesAllowed("user")
    public <T extends IObject> Set<Long> resetDefaultsByOwnerInSet(
            Class<T> klass, Set<Long> nodeIds)
    {
        // Pre-process our list of potential containers. This will resolve down
        // to a list of Pixels objects for us to work on.
        List<Pixels> pixelsList = new ArrayList<Pixels>();
        updatePixelsForNodes(pixelsList, klass, nodeIds);
        Map<Long, RenderingDef> ownerSettings =
            loadRenderingSettingsByOwner(pixelsList);
        Map<Long, RenderingDef> mySettings =
            loadRenderingSettings(pixelsList);
        Set<IObject> toSave = new HashSet<IObject>();
        Set<Long> toReturn = new HashSet<Long>();
        for (Pixels pixels : pixelsList)
        {
            RenderingDef from = ownerSettings.get(pixels.getId());
            RenderingDef to = mySettings.get(pixels.getId());
            try
            {
                toSave.add(applySettings(pixels, pixels, from, to));
                toReturn.add(pixels.getImage().getId());
            }
            catch (Exception e)
            {
                log.warn(String.format(
                        "Exception while applying settings from owner. " +
                        "%s from %s to %s", pixels, from, to), e);
            }
        }
        iUpdate.saveCollection(toSave);
        return toReturn;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F. 
     * @see IRenderingSettings#resetMinMaxInSet(Class, Set)
     */
    @RolesAllowed("user")
    public <T extends IObject> Set<Long> resetMinMaxInSet(Class<T> klass,
                                                          Set<Long> nodeIds)
    {
        StopWatch s1 = new CommonsLogStopWatch("omero.resetMinMaxInSet");
        // Load our dependencies for rendering settings manipulation
        List<Family> families = pixelsMetadata.getAllEnumerations(Family.class);
        List<RenderingModel> renderingModels = 
            pixelsMetadata.getAllEnumerations(RenderingModel.class);
        // Pre-process our list of potential containers. This will resolve down
        // to a list of Pixels objects for us to work on.
        List<Pixels> pixelsList = new ArrayList<Pixels>();
        updatePixelsForNodes(pixelsList, klass, nodeIds);
        Map<Long, RenderingDef> mySettings =
            loadRenderingSettings(pixelsList);
        Set<IObject> toSave = new HashSet<IObject>();
        Set<Long> toReturn = new HashSet<Long>();
        for (Pixels pixels : pixelsList)
        {
            RenderingDef settings = mySettings.get(pixels.getId());
            if (settings == null)
            {
                try
                {
                    resetDefaults(settings, pixels, false, false, families,
                                  renderingModels);
                    toReturn.add(pixels.getId());
                    toSave.add(settings);
                }
                catch (Exception e)
                {
                    log.warn("Exception while resetting settings.", e);
                }
            }
            else
            {
                for (int i = 0; i < pixels.sizeOfChannels(); i++)
                {
                    ChannelBinding cb = settings.getChannelBinding(i);
                    StatsInfo stats = pixels.getChannel(i).getStatsInfo();
                    cb.setInputStart(stats.getGlobalMin());
                    cb.setInputEnd(stats.getGlobalMax());
                }
                toReturn.add(pixels.getId());
                toSave.add(settings);
            }
            // Increment the version of the rendering settings so that we 
            // can have some notification that either the RenderingDef 
            // object itself or one of its children in the object graph has 
            // been updated. FIXME: This should be implemented using 
            // IUpdate.touch() or similar once that functionality exists.
            settings.setVersion(settings.getVersion() + 1);
        }
        iUpdate.saveCollection(toSave);
        s1.stop();
        return toReturn;
    }

    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#setOriginalSettingsInImage(long)
     */
    @RolesAllowed("user")
    public void setOriginalSettingsInImage(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	setOriginalSettingsInSet(Image.class, nodeIds);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#setOriginalSettingsForPixels(long)
     */
    @RolesAllowed("user")
    public void setOriginalSettingsForPixels(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	setOriginalSettingsInSet(Pixels.class, nodeIds);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F.
     * @see IRenderingSettings#setOriginalSettingsInDataset(long)
     */
    @RolesAllowed("user")
    public Set<Long> setOriginalSettingsInDataset(long to) {
    	Set<Long> nodeIds = new HashSet<Long>();
    	nodeIds.add(to);
    	return setOriginalSettingsInSet(Dataset.class, nodeIds);
    }
    
    /**
     * Implemented as specified by the {@link IRenderingSettings} I/F. 
     * @see IRenderingSettings#setOriginalSettingsInSet(Class, Set)
     */
    @RolesAllowed("user")
    public <T extends IObject> Set<Long> setOriginalSettingsInSet(
            Class<T> klass, Set<Long> nodeIds)
    {
    	return resetDefaultsInSet(klass, nodeIds, false);
    }
}