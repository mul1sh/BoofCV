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

import org.ddogleg.util.PrimitiveArrays;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Peter Abeles
 */
class TestHasherLlah_F64 {

	Random rand = new Random(345);

	@Test
	void computeHashAffine() {
		fail("Implement");
	}

	@Test
	void computeHashPerspective() {
		fail("Implement");
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

		var alg = new HasherLlah_F64();

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