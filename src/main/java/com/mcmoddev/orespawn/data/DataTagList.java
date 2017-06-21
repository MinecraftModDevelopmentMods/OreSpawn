package com.mcmoddev.orespawn.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/*
 * Note that this would probably be a lot better done
 * with an Interface covering the data-store types, and DataTagList
 * itself implementing the interface. That can be done at a later date.
 */
public class DataTagList {
	private HashMap<String, DataTagList> subLists;
	private HashMap<String, DataTag> subTags;
	private List<String> usedNames;
	private String myName;
		
	public DataTagList() {
		this.subLists = new HashMap<>();
		this.subTags = new HashMap<>();
		this.usedNames = new ArrayList<>();
		this.myName = "";
	}
	
	public DataTagList(String name) {
		this.subLists = new HashMap<>();
		this.subTags = new HashMap<>();
		this.usedNames = new ArrayList<>();
		this.myName = name;
	}
	
	public DataTagList getList(String keyname) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subLists.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a TagList!", keyname));
			} else {
				return this.subLists.get(keyname);
			}
		} else {
			DataTagList newTagList = new DataTagList(keyname);
			this.subLists.put(keyname,  newTagList);
			this.usedNames.add(keyname);
			return newTagList;
		}
	}
	
	public DataTag getTag(String keyname) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				return this.subTags.get(keyname);
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			this.subTags.put(keyname,  newTag);
			this.usedNames.add(keyname);
			return newTag;
		}
	}
	
	public boolean getBoolean(String keyname, boolean defaultValue) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				boolean rv = defaultValue;
				try {
					rv = this.subTags.get(keyname).getBoolean();
				} catch( Exception e) {
					throw e;
				}
				return rv;
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			newTag.setBoolean(defaultValue);
			this.subTags.put(keyname, newTag);
			this.usedNames.add(keyname);
			return defaultValue;
		}
	}
	
	public int getInteger(String keyname, int defaultValue) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				int rv = defaultValue;
				try {
					rv = this.subTags.get(keyname).getInteger();
				} catch( Exception e) {
					throw e;
				}
				return rv;
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			newTag.setInteger(defaultValue);
			this.subTags.put(keyname, newTag);
			this.usedNames.add(keyname);
			return defaultValue;
		}
	}
	
	public float getFloat(String keyname, float defaultValue) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				float rv = defaultValue;
				try {
					rv = this.subTags.get(keyname).getFloat();
				} catch( Exception e) {
					throw e;
				}
				return rv;
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			newTag.setFloat(defaultValue);
			this.subTags.put(keyname, newTag);
			this.usedNames.add(keyname);
			return defaultValue;
		}
	}
	
	public String getString(String keyname, String defaultValue) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				String rv = defaultValue;
				try {
					rv = this.subTags.get(keyname).getString();
				} catch( Exception e) {
					throw e;
				}
				return rv;
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			newTag.setString(defaultValue);
			this.subTags.put(keyname, newTag);
			this.usedNames.add(keyname);
			return defaultValue;
		}
	}
	
	public String getName() {
		return this.myName;
	}
	
	public Collection getArray(String keyname) throws Exception {
		if( this.usedNames.contains(keyname) ) {
			if(!this.subTags.containsKey(keyname)) {
				throw new Exception(String.format("%s exists and is not a Tag!", keyname));
			} else {
				Collection rv = Collections.EMPTY_LIST;
				try {
					rv = this.subTags.get(keyname).getArray();
				} catch( Exception e) {
					throw e;
				}
				
				return rv;
			}
		} else {
			DataTag newTag = new DataTag(keyname);
			newTag.setArray(Collections.EMPTY_LIST);
			this.subTags.put(keyname, newTag);
			this.usedNames.add(keyname);
			return Collections.EMPTY_LIST;
		}
	}
	
	public void setDataTagList(String keyname, DataTagList tagList) throws Exception {
		if( !keyname.equals(tagList.getName()) ) {
			if( (this.usedNames.contains(keyname)) && (this.subLists.containsKey(keyname)) ) {
				// TODO: Log This - we're replacing an entire tag list
				this.subLists.put(keyname, tagList);
			} else if( !this.usedNames.contains(keyname) ) {
				this.subLists.put(keyname, tagList);
				this.usedNames.add(keyname);
			} else {
				if( "".equals(tagList.getName())) {
					throw new Exception("Name conflict and TagList has a blank name!");
				} else {
					// TODO: Log This - We're using the TagList name and not the keyname passed in to try and avoid a conflict
					String altName = tagList.getName();
					if( this.usedNames.contains(altName) ) {
						if( !this.subLists.containsKey(altName) ) {
							throw new Exception("Name conflict on both primary and backup names!");
						} else {
							this.subLists.put(altName, tagList);
						}
					} else {
						this.subLists.put(altName, tagList);
						this.usedNames.add(altName);
					}
				}
			}
		}
	}
	
	public void setDataTag(String keyname, DataTag tag) throws Exception {
		if( !keyname.equals(tag.getName()) ) {
			if( (this.usedNames.contains(keyname)) && (this.subTags.containsKey(keyname)) ) {
				this.subTags.put(keyname, tag);
			} else if( !this.usedNames.contains(keyname) ) {
				this.subTags.put(keyname, tag);
				this.usedNames.add(keyname);
			} else {
				if( "".equals(tag.getName())) {
					throw new Exception("Name conflict and Tag has a blank name!");
				} else {
					// TODO: Log This - We're using the Tag name and not the keyname passed in to try and avoid a conflict
					String altName = tag.getName();
					if( this.usedNames.contains(altName) ) {
						if( !this.subTags.containsKey(altName) ) {
							throw new Exception("Name conflict on both primary and backup names!");
						} else {
							this.subTags.put(altName, tag);
						}
					} else {
						this.subTags.put(altName, tag);
						this.usedNames.add(altName);
					}
				}
			}
		}
	}
}
