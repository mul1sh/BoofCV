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

import georegression.geometry.UtilPoint2D_F64;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.util.PrimitiveArrays;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Abeles
 */
class TestHasherLlah_F64 {

	Random rand = new Random(345);

	@Test
	void computeHashAffine() {
		List<Point2D_F64> points = UtilPoint2D_F64.random(-5,5,4,rand);

		var alg = new HasherLlah_F64(5,200);
		alg.setSamples(createSamples());
		var feature = new LlahFeature(1);
		alg.computeHashAffine(points,feature);
		checkFeature(feature);

		points = UtilPoint2D_F64.random(-5,5,6,rand);
		feature = new LlahFeature(15);
		alg.computeHashAffine(points,feature);
		checkFeature(feature);
	}

	private double[] createSamples() {
		double[] samples = new double[8];

		samples[0] = 0.05;
		for (int i = 1; i < samples.length; i++) {
			samples[i] = samples[i-1]+rand.nextDouble();
		}
		return samples;
	}

	private void checkFeature( LlahFeature feature ) {
		assertTrue(feature.hashCode != 0);
		assertNull(feature.next);
		boolean allZero = true;
		for (int i = 0; i < feature.invariants.length; i++) {
			if( feature.invariants[i] != 0 ) {
				allZero = false;
				break;
			}
		}
		assertFalse(allZero);
	}

	@Test
	void computeHashPerspective() {
		List<Point2D_F64> points = UtilPoint2D_F64.random(-5,5,5,rand);

		var alg = new HasherLlah_F64(5,200);
		alg.setSamples(createSamples());
		var feature = new LlahFeature(1);
		alg.computeHashPerspective(points,feature);
		checkFeature(feature);

		points = UtilPoint2D_F64.random(-5,5,7,rand);
		feature = new LlahFeature(21);
		alg.computeHashPerspective(points,feature);
		checkFeature(feature);
	}

	@Test
	void discretize() {
		fail("Implement");
	}

	@Test
	void learnDiscretization() {
		int N = 120;
		double[] invariants = new double[N+20];
		// high density
		for (int i = 0; i < 30; i++) {
			invariants[i] = i;
		}
		// low density
		for (int i = 30; i < N; i++) {
			invariants[i] = (i-30)*2.5+30;
		}
		// ignored
		for (int i = 0; i < 20; i++) {
			invariants[N+i] = 12.1f;
		}
		// shuffle the order to make sure it's sorted
		PrimitiveArrays.shuffle(invariants,0,N,rand);

		var alg = new HasherLlah_F64(1,1);

		// this total number of discrete values is larger than the actual number of unique values
		// and the look up table size is over kill. This should yield perfect results
		alg.learnDiscretization(invariants,N,150);
		check(alg,invariants,N,120);

		// under sample the invariants
		alg.learnDiscretization(invariants,N,80);
		check(alg,invariants,N,80);
	}

	private void check( HasherLlah_F64 alg , double[] invariants , int length , int expectedUnique ) {
		int minD = Integer.MAX_VALUE;
		int maxD = -Integer.MAX_VALUE;

		var uniqueSet = new HashSet<Integer>();

		for (int i = 0; i < length; i++) {
			int d = alg.discretize(invariants[i]);
			uniqueSet.add(d);
			minD = Math.min(minD,d);
			maxD = Math.max(maxD,d);
		}

		int N = alg.getNumValues();

		// check a value out of range
		assertEquals(0,alg.discretize(-1000));
		assertEquals(N-1,alg.discretize(1000));

		assertEquals(0,minD);
		assertEquals(N-1,maxD);
		if( expectedUnique > 0 )
			assertEquals(expectedUnique,uniqueSet.size());

	}
}