/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.describe.llah;

import boofcv.alg.geo.PerspectiveOps;
import georegression.struct.point.Point2D_F64;
import lombok.Getter;
import org.ddogleg.combinatorics.Combinations;

import java.util.Arrays;
import java.util.List;

/**
 * Functions related to computing the hash values of a feature
 *
 * @author Peter Abeles
 */
public class HasherLlah_F64 {

	/** The Number discrete values possible */
	@Getter private int numDiscreteValues;

	/** The minimum invariant value found in training set */
	@Getter private double minContinuousValue;
	/** The range of possible invariant values found in training set */
	@Getter private double rangeContinuousValue;
	/** Lookup table used to convert an invariant into a discrete value */
	@Getter private int[] lookupTable;

	/**
	 * k^i in the hash function
	 */
	private long hashK;
	/**
	 * The maximum value of the hash code
	 */
	private int hashSize;

	// Used to compute all the combinations of a set
	private Combinations<Point2D_F64> combinator = new Combinations<>();

	/**
	 * Compuets the hashcode for affine
	 */
	public void computeHashAffine(List<Point2D_F64> points , LlahFeature output ) {
		combinator.init(points,4);
		long hash = 0;
		int i = 0;
		long k = 1;
		while( combinator.next() ) {
			Point2D_F64 p1 = combinator.get(0);
			Point2D_F64 p2 = combinator.get(1);
			Point2D_F64 p3 = combinator.get(2);
			Point2D_F64 p4 = combinator.get(3);
			double invariant = PerspectiveOps.invariantAffine(p1,p2,p3,p4);
			int r = output.invariants[i++] = discretize(invariant);

			hash += r*k;

			k *= hashK;
		}

		output.hashCode = (int)(hash % hashSize);
	}

	/**
	 * Compuets the hashcode for perspective
	 */
	public void computeHashPerspective(List<Point2D_F64> points , LlahFeature output ) {
		combinator.init(points,5);
		long hash = 0;
		int i = 0;
		long k = 1;
		while( combinator.next() ) {
			Point2D_F64 p1 = combinator.get(0);
			Point2D_F64 p2 = combinator.get(1);
			Point2D_F64 p3 = combinator.get(2);
			Point2D_F64 p4 = combinator.get(3);
			Point2D_F64 p5 = combinator.get(4);
			double invariant = PerspectiveOps.invariantProjective(p1,p2,p3,p4,p5);
			int r = output.invariants[i++] = discretize(invariant);

			hash += r*k;

			k *= hashK;
		}

		output.hashCode = (int)(hash % hashSize);
	}

	/**
	 * Computes the discrete value from the continuous valued invariant
	 */
	public int discretize( double invariant ) {
		if( invariant >= minContinuousValue+rangeContinuousValue )
			return numDiscreteValues-1;
		else if( invariant < minContinuousValue )
			return 0;

		// fractional location in the range of possible values
		double locationFrac = (invariant-minContinuousValue)/rangeContinuousValue;
		return lookupTable[ (int)((lookupTable.length-1)*locationFrac)];
	}

	/**
	 * Specifies how to discretize the continuous invariants
	 */
	public void setDiscretization(int[] lookupTable, double minContinuousValue, int numDiscreteValues, double rangeContinuousValue) {
		this.numDiscreteValues = numDiscreteValues;
		this.minContinuousValue = minContinuousValue;
		this.rangeContinuousValue = rangeContinuousValue;
		this.lookupTable = lookupTable;
	}

	/**
	 * From a dataset of computed found feature invariants, compute a look up table to convert the
	 * continuous values into discrete values. The table is designed to have more possible values where
	 * there are more continuous values.
	 *
	 * @param invariants Set of computed feature invariant values.
	 * @param length Number of invariants.
	 * @param lookupSize Number of elements in lookup table. Large values means it can fit better to distribution of
	 *                     invariant values
	 * @param numDiscrete Number of possible discrete values. Larger values indicate higher resolution in discretation
	 */
	public void learnDiscretization( double[] invariants , int length , int lookupSize, int numDiscrete ) {
		this.lookupTable = new int[lookupSize];
		this.numDiscreteValues = numDiscrete;

		Arrays.sort(invariants,0,length);
		this.minContinuousValue = invariants[0];
		this.rangeContinuousValue = invariants[length-1]-minContinuousValue;

		for (int i = 0; i < numDiscreteValues; i++) {
			int idx0 = i*length/numDiscreteValues;
			int idx1 = (i+1)*length/numDiscreteValues;

			double frac0 = (invariants[idx0]-minContinuousValue)/rangeContinuousValue;
			double frac1 = (invariants[idx1]-minContinuousValue)/rangeContinuousValue;


			int table0 = (int)((length-1)*frac0);
			int table1 = (int)((length-1)*frac1)+1;

			for (int j = table0; j < table1; j++) {
				lookupTable[j] = i;
			}
		}
	}
}
