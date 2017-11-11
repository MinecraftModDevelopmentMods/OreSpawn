package com.mcmoddev.orespawn.util;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import net.minecraft.block.state.IBlockState;

public class OreList {
	private List<Integer> myChanceList;
	private List<OreBuilder> myCopy;
	
	private Integer listCount = 0;
	
	public OreList() {
		this.myChanceList = new LinkedList<>();
		this.myCopy = new LinkedList<>();
	}
	
	public void build(List<OreBuilder> ores) {
		ores.stream().sorted( Comparator.comparingInt ( OreBuilder::getChance ) )
		    .forEach( ob -> { myChanceList.add(ob.getChance()); myCopy.add(ob); } );
		
		this.listCount = myChanceList.stream().mapToInt(Integer::intValue).max().getAsInt();
	}
	
	public OreBuilder getRandomOre(Random rand) {
		int v = rand.nextInt(this.listCount);
		
		int c = 0;
		for( Integer i : this.myChanceList ) {
			c += i;
			if( c > v ) {
				OreBuilder rv = this.getOreWithChance( i );
				if( rv == null )
					break;
				else
					return rv;
			}
		}
		
		return this.getMaxChanceOre();
	}

	private OreBuilder getMaxChanceOre() {
		return this.getOreWithChance(this.myChanceList.stream().mapToInt( Integer::intValue ).max().getAsInt());
	}

	private OreBuilder getOreWithChance(int intValue) {
		for( OreBuilder o : this.myCopy ) {
			if( o.getChance() == intValue ) {
				return o;
			}
		}
		
		return null;
	}

	public ImmutableList<IBlockState> getOres() {
		return ImmutableList.copyOf(this.myCopy.stream().map( OreBuilder::getOre ).distinct().collect( Collectors.toList () ) );
	}
}
