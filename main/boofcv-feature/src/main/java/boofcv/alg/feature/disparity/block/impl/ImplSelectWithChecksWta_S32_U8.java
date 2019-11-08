/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.feature.disparity.block.impl;

import boofcv.alg.feature.disparity.block.DisparitySelect;
import boofcv.struct.image.GrayU8;

/**
 * <p>
 * Implementation of {@link ImplSelectWithChecksBase_S32} for {@link GrayU8}.
 * </p>
 *
 * @author Peter Abeles
 */
public class ImplSelectWithChecksWta_S32_U8 extends ImplSelectWithChecksBase_S32<GrayU8>
{
	public ImplSelectWithChecksWta_S32_U8(int maxError, int rightToLeftTolerance, double texture) {
		super(maxError, rightToLeftTolerance, texture);
	}

	public ImplSelectWithChecksWta_S32_U8(ImplSelectWithChecksWta_S32_U8 original) {
		super(original);
	}

	@Override
	public void configure(GrayU8 imageDisparity, int minDisparity, int maxDisparity, int radiusX) {
		super.configure(imageDisparity, minDisparity, maxDisparity, radiusX);

		if( rangeDisparity > 254 )
			throw new IllegalArgumentException("(max - min) disparity must be <= 254");
	}

	@Override
	public DisparitySelect<int[], GrayU8> concurrentCopy() {
		return new ImplSelectWithChecksWta_S32_U8(this);
	}

	protected void setDisparity( int index , int value ) {
		imageDisparity.data[index] = (byte)value;
	}

	@Override
	public Class<GrayU8> getDisparityType() {
		return GrayU8.class;
	}
}