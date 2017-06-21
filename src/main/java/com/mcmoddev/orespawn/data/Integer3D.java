package com.mcmoddev.orespawn.data;

public class Integer3D {
		/**
		 * X-coordinate of X,Y,Z coordinate 
		 */
		public final int X;
		/**
		 * Y-coordinate of X,Y,Z coordinate 
		 */
		public final int Y;
		/**
		 * Z-coordinate of X,Y,Z coordinate 
		 */
		public final int Z;
		/**
		 * Creates an integer pair to be used as 2D coordinates
		 * @param x X-coordinate of X,Y,Z coordinate 
		 * @param y Y-coordinate of X,Y,Z coordinate 
		 * @param z Z-coordinate of X,Y,Z coordinate 
		 */
		public Integer3D(int x, int y, int z){
			this.X = x;
			this.Y = y;
			this.Z = z;
		}
		@Override
		public int hashCode(){
			return ((X<< 8) ^ ((Y) ) ^ ((Z) << 16) * 17); 
		}
		@Override
		public boolean equals(Object o){
			if(this == o) return true;
			if(o instanceof Integer3D){
				Integer3D other = (Integer3D)o;
				return other.X == this.X && other.Y == this.Y && other.Z == this.Z;
			}
			return false;
		}

}
