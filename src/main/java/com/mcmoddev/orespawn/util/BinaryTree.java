package com.mcmoddev.orespawn.util;

import java.util.Random;

import com.mcmoddev.orespawn.api.os3.OreBuilder;

public class BinaryTree {
	private TreeNode root;
	private int maxVal;
	
	private BinaryTree() {
		this.root = new TreeNode();
	}
	
	public BinaryTree(int median) {
		this();
		this.maxVal = (median * 2) + 1;
		this.root.setNodeId(median);
	}
	
	public int getMax() {
		return this.maxVal;
	}
	
	public OreBuilder findMatchingNode(int value) {
		TreeNode actualNode = this.root.findNode(value);
		return actualNode.getValue();
	}
	
	public void addNode(OreBuilder value, int nodeId) {
		this.root.addNode(value, nodeId);
	}

	public void makeRoot(OreBuilder oreBuilder) {
		this.root.setValue(oreBuilder);
		this.root.setNodeId(50);
	}
	
	public OreBuilder getRandomOre(Random rand) {
		return this.findMatchingNode(rand.nextInt(this.maxVal));
	}
}
