/*
 * org.openmicroscopy.shoola.agents.editor.model.Field
 * 
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2008 University of Dundee. All rights reserved.
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
 *	author Will Moore will@lifesci.dundee.ac.uk
 */

package org.openmicroscopy.shoola.agents.editor.model;

// Java imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.TableModel;

//Third-party libraries

//Application-internal dependencies

import org.openmicroscopy.shoola.agents.editor.model.params.AbstractParam;
import org.openmicroscopy.shoola.agents.editor.model.params.BooleanParam;
import org.openmicroscopy.shoola.agents.editor.model.params.EditorLinkParam;
import org.openmicroscopy.shoola.agents.editor.model.params.EnumParam;
import org.openmicroscopy.shoola.agents.editor.model.params.IParam;
import org.openmicroscopy.shoola.agents.editor.model.params.NumberParam;
import org.openmicroscopy.shoola.agents.editor.model.params.TextParam;
import org.openmicroscopy.shoola.agents.editor.model.tables.TableModelFactory;

/**
 * This is the data object that occupies a node of the tree hierarchy. 
 * It has name, description, url etc, stored in an AttributeMap, and may
 * have 0, 1 or more Parameter objects {@link IParam} to store 
 * experimental variables, or parameters. 
 * 
 * @author  William Moore &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:will@lifesci.dundee.ac.uk">will@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
public class Field 
	implements IField,
	Cloneable {
	
	/**
	 * A property of this field. The attribute for an (optional) Name.
	 */
	public static final String 			FIELD_NAME = "fieldName";
	
	/**
	 * A property of this field. 
	 * Stores a color as a string in the form "r:g:b";
	 */
	public static final String 			BACKGROUND_COLOUR = "backgroundColour";
	
	/**  The name of the attribute used to store a step type, 
	 * Possible values are SINGLE_STEP, STEP_GROUP, SPLIT_STEP.
	 */
	public static final String 			STEP_TYPE = "step-type";
	
	/**
	 * A display property of this field.
	 * getDisplayAttribute(TOOL_TIP_TEXT) should return a string composed
	 * of field description and parameter values etc. 
	 */
	public static final String 			TOOL_TIP_TEXT = "toolTipText";
	
	/**
	 * The list of Parameters, representing experimental variables for this 
	 * field.
	 */
	private List<IFieldContent> 		fieldParams;

	/**
	 * A map of the template attributes for this Field. 
	 * eg Name, Description etc. 
	 */
	private HashMap<String, String> 	templateAttributesMap;
	
	/**
	 * If the parameters of this field have multiple values, they can be 
	 * represented as a {@link TableModel} here. 
	 */
	private TableModel 					tableData;
	
	/**
	 * A list of notes that can be added to a Field/Step when a protocol is
	 * performed, to become an experiment. 
	 */
	private	List<Note>					stepNotes;
	
	/**
	 * A list of data-references that link to data generated by this step. 
	 */
	private List<DataReference>			dataRefs;
	
	/**
	 * Default constructor.
	 */
	public Field() 
	{
		templateAttributesMap = new HashMap<String, String>();
		fieldParams = new ArrayList<IFieldContent>();
		stepNotes = new ArrayList<Note>();
		dataRefs = new ArrayList<DataReference>();
	}
	
	/**
	 * Returns a copy of this object.
	 * This is implemented manually, rather than calling super.clone()
	 * Therefore, any subclasses should also manually override this method to 
	 * copy any additional attributes they have.  
	 */
	public IField clone() 
	{
		Field newField = new Field();
		
		// duplicate attributes
		HashMap<String,String> newAtt = new HashMap<String,String>(getAllAttributes());
		newField.setAllAttributes(newAtt);
		
		IFieldContent newContent;
		// duplicate parameters 
		for (int i=0; i<getContentCount(); i++) {
			IFieldContent content = getContentAt(i);
			if (content instanceof IParam) {
				IParam param = (IParam)content;
				newContent = param.cloneParam();
				newField.addContent(newContent);
			} else if (content instanceof TextContent) {
				TextContent text = (TextContent)content;
				newContent = new TextContent(text);	// clone content
				newField.addContent(newContent);
			}
		}
		
		// if tableModelAdaptor exists for this field, add one to new field
		if (getTableData() != null) {
			TableModel tm = TableModelFactory.getFieldTable(newField);
			newField.setTableData(tm);
		}
		
		// need to duplicate notes? data-references? 
		
		return newField;
	}
	
	/**
	 * A constructor used to set the name of the field.
	 * This constructor is called by the others, in order to initialise
	 * the attributesMap and parameters list. 
	 * 
	 * @param name		A name given to this field. 
	 */
	public Field(String name) 
	{	
		this();
		
		setAttribute(FIELD_NAME, name);
	}
	
	/**
	 * gets an attribute in the templateAttributesMap
	 * 
	 * Implemented as specified by the {@link IAttributes} interface
	 * 
	 * @see IAttributes#getAttribute(String)
	 */
	public String getAttribute(String name) 
	{
		return templateAttributesMap.get(name);
	}
	
	/**
	 * gets all attributes in the templateAttributesMap
	 */
	public Map getAllAttributes() {
		return templateAttributesMap;
	}
	
	/**
	 * sets the attribute map.
	 * 
	 * @param newAtt	The new attribute map
	 */
	public void setAllAttributes(HashMap<String,String> newAtt) {
		templateAttributesMap = newAtt;
	}
	
	/**
	 * sets an attribute in the attributesMap
	 * Implemented as specified by the {@link IAttributes} interface
	 * 
	 * @see IAttributes#setAttribute(String, String)
	 */
	public void setAttribute(String name, String value) {
		
		templateAttributesMap.put(name, value);
	}
	
	/**
	 * For display etc. Simply returns the name...
	 */
	public String toString() {
		String name = getAttribute(FIELD_NAME);
		
		return (name == null ? "Step Name" : name);
	}

	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Convenience method for querying the attributes map for 
	 * boolean attributes.
	 */
	public boolean isAttributeTrue(String attributeName) {
		String value = getAttribute(attributeName);
		return DataFieldConstants.TRUE.equals(value);
	}
	

	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * This method tests to see whether the field has any parameters 
	 * that have not been filled. 
	 * ie, Has the user entered a "valid" value into the Form. 
	 * 
	 * @return	number of parameters have not been filled out by user.  
	 */
	public int getUnfilledCount() {
		return getUnfilledCount(false);
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * This method tests to see whether the field has any parameters 
	 * that have not been filled.
	 * 
	 * @param	if true, will only check 'required' parameters.
	 * @return	number of parameters have not been filled out by user.  
	 */
	public int getUnfilledCount(boolean requiredOnly) {
		
		int unfilledCount = 0;
		IParam param;
		for (IFieldContent content : fieldParams) {
			if (content instanceof IParam) {
				param = (IParam)content;
				// if you only want 'required' parameters, but this one isn't..
				if (requiredOnly && (! param.isAttributeTrue(
											AbstractParam.PARAM_REQUIRED))) {
					continue;
				}
				if (! param.isParamFilled()) {
					unfilledCount++;
				}
			}
		}
		return unfilledCount;
	}

	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Returns the number of IParam parameters for this field.
	 */
	public int getContentCount() {
		return fieldParams.size();
	}

	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Returns the content of this field at the given index.
	 */
	public IFieldContent getContentAt(int index) {
		return fieldParams.get(index);
	}

	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Adds a parameter to the list for this field
	 */
	public void addContent(IFieldContent param) {
		if (param != null)
			fieldParams.add(param);
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Adds a parameter to the list for this field
	 */
	public void addContent(int index, IFieldContent param) 
	{
		if (param != null)
			fieldParams.add(index, param);
	}

	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Removes the specified content from the list. 
	 */
	public int removeContent(IFieldContent param) 
	{
		int index = fieldParams.indexOf(param);
		
		fieldParams.remove(param);
		return index;
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * Removes the specified content from the list. 
	 */
	public void removeContent(int index) 
	{
		fieldParams.remove(index);
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * 
	 * @see IField#getAtomicParams()
	 */
	public List<IParam> getAtomicParams() 
	{
		List<IParam> params = new ArrayList<IParam>();
		
		// check Boolean, Enum, Text, Number
		// Don't count Date-Time or Ontology parameters as "Atomic" since they
		// are defined by several parameters. 
		for (IFieldContent content : fieldParams) {
			if ((content instanceof BooleanParam) ||
					(content instanceof EnumParam) ||
					(content instanceof TextParam)|| 
					(content instanceof EditorLinkParam)|| 
					(content instanceof NumberParam)){
				params.add((IParam)content);
			}
		}
		
		return params;
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * 
	 * @see IField#getParams()
	 */
	public List<IParam> getParams() 
	{
		List<IParam> params = new ArrayList<IParam>();
		
		for (IFieldContent content : fieldParams) {
			if (content instanceof AbstractParam) {
				params.add((IParam)content);
			}
		}
		
		return params;
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * @see {@link IField#addNote(Note)}
	 */
	public void addNote(Note note) { stepNotes.add(note); }
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * @see {@link IField#addNote(Note, int)}
	 */
	public void addNote(Note note, int index) { stepNotes.add(index, note); }
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * @see {@link IField#removeNoteAt(Note);
	 */
	public int removeNote(Note note) 
	{
		int index = stepNotes.indexOf(note);
		
		stepNotes.remove(note);
		return index;
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * @see {@link IField#getNoteCount()}
	 */
	public int getNoteCount() { return stepNotes.size(); }
	
	/**
	 * Implemented as specified by the {@link IField} interface.
	 * @see {@link IField#getNoteAt(int)}
	 */
	public Note getNoteAt(int index) { return stepNotes.get(index); }
	
	/**
	 * Returns a String containing the field description, plus the 
	 * tool-tip-text from it's parameters. 
	 * 
	 * @return		see above.
	 */
	public String getToolTipText() 
	{
		String toolTipText = "";
		
		String paramText;
		
		List<IParam> params = getAtomicParams();
		for (IParam param : params) {
			paramText = param.toString();
			if (toolTipText.length() > 0) 
				toolTipText = toolTipText + ", ";
			toolTipText = toolTipText + paramText;
		}
		
		return toolTipText;
	}
	
	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * Sets {@link #tableData}
	 * 
	 * @see IField#setTableData(TableModel)
	 */
	public void setTableData(TableModel tableModel)
	{
		tableData = tableModel;
	}

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * Returns {@link #tableData}
	 * 
	 * @see IField#getTableData()
	 */
	public TableModel getTableData() { return tableData; }

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * 
	 * @see IField#addDataRef(DataReference)
	 */
	public void addDataRef(DataReference ref) {
		dataRefs.add(ref);
	}

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * 
	 * @see IField#getDataRefAt(int)
	 */
	public DataReference getDataRefAt(int index) {
		return dataRefs.get(index);
	}

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * 
	 * @see IField#getDataRefAt(int)
	 */
	public int getDataRefCount() {
		return dataRefs.size();
	}

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * 
	 * @see IField#addDataRef(int, DataReference)
	 */
	public void addDataRef(int index, DataReference ref) {
		dataRefs.add(index, ref);
	}

	/**
	 * Implemented as specified by the {@link IField} interface. 
	 * 
	 * @see IField#removeDataRef(DataReference)
	 */
	public int removeDataRef(DataReference ref) {
		int index = dataRefs.indexOf(ref);
		
		dataRefs.remove(ref);
		return index;
	}
}