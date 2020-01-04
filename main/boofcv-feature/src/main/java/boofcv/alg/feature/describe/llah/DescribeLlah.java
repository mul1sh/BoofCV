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

/**
 * Locally Likely Arrangement Hashing (LLAH) [1] computes a descriptor for a landmark based on the local geometry of
 * neighboring landmarks on the image plane. Originally proposed for document retrieval. These features are either
 * invariant to perspective or affine transforms.
 *
 * <ol>
 *     <li>Nakai, Tomohiro, Koichi Kise, and Masakazu Iwamura.
 *     "Use of affine invariants in locally likely arrangement hashing for camera-based document image retrieval."
 *     International Workshop on Document Analysis Systems. Springer, Berlin, Heidelberg, 2006.</li>
 * </ol>
 *
 * @author Peter Abeles
 */
public class DescribeLlah {
}
