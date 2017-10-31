package com.mcmoddev.orespawn.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.mcmoddev.orespawn.api.os3.OreBuilder;

public class OreList {
	private List<Integer> oreList;
	private List<OreBuilder> myCopy;
	
	private Integer listCount = 0;
	
	public OreList() {
		this.oreList = new LinkedList<>();
		this.myCopy = new LinkedList<>();
	}
	
	public void build(List<OreBuilder> ores) {
		ores.stream().sorted( (o,x) -> Integer.compare( o.getChance(), x.getChance()) )
		    .forEach( ob -> { oreList.add(ob.getChance()); myCopy.add(ob); } );
		
		this.listCount = oreList.stream().mapToInt(Integer::intValue).max().getAsInt();
	}
	
	public OreBuilder getRandomOre(Random rand) {
		int v = rand.nextInt(this.listCount);
		
		int c = 0;
		for( Integer i : this.oreList ) {
			c += i;
			if( c > v ) {
				OreBuilder rv = this.getOreWithChance(i.intValue());
				if( rv == null )
					break;
				else
					return rv;
			}
		}
		
		return this.getMaxChanceOre();
	}

	private OreBuilder getMaxChanceOre() {
		return this.getOreWithChance(this.oreList.stream().mapToInt( Integer::intValue ).max().getAsInt());
	}

	private OreBuilder getOreWithChance(int intValue) {
		for( OreBuilder o : this.myCopy ) {
			if( o.getChance() == intValue ) {
				return o;
			}
		}
		
		return null;
	}

}
