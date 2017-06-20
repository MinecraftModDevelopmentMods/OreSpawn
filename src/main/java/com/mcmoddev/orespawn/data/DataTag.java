package com.mcmoddev.orespawn.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class DataTag {
	private String myName = "";
	private valType myType = valType.NONE;
	private dataStore myData = new dataStore();
	
	private class dataStore {
		boolean bV;
		int iV;
		float fV;
		String sV;
		Collection cV;
	}

	private enum valType {
		BOOL, INT, FLOAT, STRING, ARRAY, NONE;
	}

	public DataTag() {
		
	}
	
	public DataTag(String name) {
		this.myName = name;
	}
	
	public boolean getBoolean() throws Exception {
		boolean rv = false;
		switch(this.myType) {
		case BOOL:
			rv = this.myData.bV;
			break;
		case INT:
			rv = (this.myData.iV == 0);
			break;
		case FLOAT:
			rv = (this.myData.fV == 0.0f);
			break;
		case STRING:
			String tmp = this.myData.sV;
			tmp.toLowerCase();
			rv = "true".equals(tmp);
			break;
		case ARRAY:
			throw new Exception("Unable to convery ARRAY to BOOL");
		case NONE:
			throw new Exception("No value has been given to this DataTag yet!");
		}
		return rv;
	}
	
	public void setBoolean(boolean val) {
		this.myType = valType.BOOL;
		this.myData.bV = val;
	}

	public int getInteger() throws Exception {
		switch( this.myType ) {
		case INT:
			return this.myData.iV;
		case FLOAT:
			return (int)this.myData.fV;
		case STRING:
		case BOOL:
			throw new Exception("Incompatible type request");
		case ARRAY:
			throw new Exception("Unable to convery ARRAY to INT");
		case NONE:
			throw new Exception("No value has been given to this DataTag yet!");
		}
		
		return -1;
	}
	
	public void setInteger(int val) {
		this.myType = valType.INT;
		this.myData.iV = val;
	}
	
	public float getFloat() throws Exception {
		switch( this.myType ) {
		case INT:
			return (float)this.myData.iV;
		case FLOAT:
			return this.myData.fV;
		case STRING:
		case BOOL:
			throw new Exception("Incompatible type request");
		case ARRAY:
			throw new Exception("Unable to convery ARRAY to FLOAT");
		case NONE:
			throw new Exception("No value has been given to this DataTag yet!");
		}
		
		return -1.0f;
	}
	
	public void setFloat(float val) {
		this.myType = valType.FLOAT;
		this.myData.fV = val;
	}

	public String getString() throws Exception {
		switch( this.myType ) {
		case INT:
			return String.format("%d", this.myData.iV);
		case FLOAT:
			return String.format("%f", this.myData.fV);
		case STRING:
			return this.myData.sV;
		case BOOL:
			return String.format("%s", this.myData.bV);
		case ARRAY:
			throw new Exception("Unable to convery ARRAY to STRING");
		case NONE:
			throw new Exception("No value has been given to this DataTag yet!");
		}
		
		return "";
	}
	
	public void setString(String val) {
		this.myType = valType.STRING;
		this.myData.sV = val;
	}
	
	public Collection getArray() throws Exception {
		if( this.myType.equals(valType.ARRAY) ) {
			return Collections.unmodifiableCollection(this.myData.cV);
		}
		throw new Exception("Tag not an ARRAY");
	}
	
	public void setArray(Collection val) {
		this.myType = valType.ARRAY;
		this.myData.cV = val;
	}
	
	public String getName() {
		return this.myName;
	}
}
