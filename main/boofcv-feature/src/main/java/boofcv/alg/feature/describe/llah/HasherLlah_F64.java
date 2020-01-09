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
import lombok.Setter;
import org.ddogleg.combinatorics.Combinations;

import java.util.Arrays;
import java.util.List;

/**
 * Functions related to computing the hash values of a feature
 *
 * @author Peter Abeles
 */
public class HasherLlah_F64 {

	/**
	 * Defines the look up table. A binary search should be used to effectively find the index of a value
	 */
	@Setter @Getter private double[] samples;

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
	 * Configues the hash function. See JavaDoc for info on variables
	 */
	public HasherLlah_F64(long hashK, int hashSize) {
		this.hashK = hashK;
		this.hashSize = hashSize;
	}

	/**
	 * Compuetes the hashcode for affine
	 *
	 * @param points Points in clockwise order around p[0]
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
			double invariant = PerspectiveOps.invariantCrossRatio(p1,p2,p3,p4,p5);
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
		return lowerBound(samples,0,samples.length,invariant);
	}

	// TODO replace with version in DDogleg PrimitiveArrays.lowerBound() once upgraded
	public static int lowerBound( double[] array, int offset , int length , double val ) {
		int count = length;
		int first = offset;
		while( count > 0 ) {
			int step = count/2;
			int idx = first+step;
			if( array[idx] < val ) {
				first = idx+1;
				count -= step+1;
			} else {
				count = step;
			}
		}
		return first;
	}

	/**
	 * Create a lookup table by sorting then sampling the invariants. This will have the desired property of
	 * having a denser set of points where there is a higher density of values.
	 *
	 * @param invariants Set of computed feature invariant values. This will be modified.
	 * @param length Number of invariants.
	 * @param numDiscrete Number of possible discrete values. Larger values indicate higher resolution in discretation
	 */
	public void learnDiscretization( double[] invariants , int length , int numDiscrete ) {
		this.samples = new double[numDiscrete];

		// sort invariants from smallest to largest
		Arrays.sort(invariants,0,length);

		// sample points evenly by index. samples[0] will be smallest value and samples[MAX] will be largest
		for (int i = 0; i < numDiscrete; i++) {
			int idx = (length-1)*i/(numDiscrete-1);
			samples[i] = invariants[idx];
		}

		// We want the output to always be from 0 to numDiscrete-1. This will ensure that
		samples[numDiscrete-1] = Double.MAX_VALUE;
	}

	public int getNumValues() {
		return samples.length;
	}
}
