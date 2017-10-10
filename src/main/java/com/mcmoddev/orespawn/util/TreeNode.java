package com.mcmoddev.orespawn.util;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.OreBuilder;

public class TreeNode {
	private TreeNode parent;
	private TreeNode left;
	private TreeNode right;
	private OreBuilder actualValue;
	private int nodeID;
	
	TreeNode() {
		this.parent = this.left = this.right = null;
	}
	
	TreeNode(TreeNode parent) {
		this();
		this.parent = parent;
	}
	
	public void setNodeId(int value) {
		if( value > 0 ) {
			this.nodeID = value;
		} else if( this.actualValue != null ) {
			this.nodeID = this.actualValue.getChance();
		}
	}
	
	public void setValue(OreBuilder value) {
		this.actualValue = value;
		this.setNodeId(0);
	}

	public int getNodeId() {
		return this.nodeID;
	}

	public TreeNode findNode(int value) {
		if( this.left == null && this.right == null ) return this;
		
		if( value < this.nodeID ) {
			return this.findLeft(value);
		} else if( value > this.nodeID) {
			return this.findRight(value);
		}
		
		return this;
	}

	private TreeNode findLeft(int value) {
		if( this.left == null ) {
			return this;
		}
		
 		return this.left.findNode(value);
	}
	
	private TreeNode findRight(int value) {
		if( this.right == null ) {
			return this;
		}
		
		if( this.right.getNodeId() > value ) {
			return this;
		}
		
		return this.right.findNode(value);
	}
	
	public OreBuilder getValue() {
		return this.actualValue;
	}

	public void addNode(OreBuilder value, int newNodesId) {
		if( newNodesId < this.nodeID ) {
			insertLeft(value, newNodesId);
		} else if( newNodesId > this.nodeID ) {
			insertRight(value, newNodesId);
		} else {
			if( this.actualValue == null ) {
				this.actualValue = value;
			} else {
				OreSpawn.LOGGER.fatal("Multiple Items With Same Node ID Value (%(0,d) - this should not happen (ignoring node!)", newNodesId);
			}
		}
	}

	public TreeNode getRight() {
		return this.right;
	}

	public TreeNode getLeft() {
		return this.left;
	}
	
	public TreeNode getParent() {
		return this.parent;
	}

	private void insertRight(OreBuilder value, int newNodesId) {
		if( this.right == null ) {
			TreeNode nn = new TreeNode();
			nn.setParent(this);
			nn.setNodeId(newNodesId);
			nn.setValue(value);
			this.right = nn;
		} else {
			this.right.addNode(value, newNodesId);
		}
	}

	private void insertLeft(OreBuilder value, int newNodesId) {
		if( this.left == null ) {
			TreeNode nn = new TreeNode();
			nn.setParent(this);
			nn.setNodeId(newNodesId);
			nn.setValue(value);
			this.left = nn;
		} else {
			this.left.addNode(value, newNodesId);
		}
	}

	private void setParent(TreeNode nn) {
		this.parent = nn;
	}
}
