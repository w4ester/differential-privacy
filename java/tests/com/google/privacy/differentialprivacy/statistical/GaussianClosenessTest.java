//
// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.privacy.differentialprivacy.statistical;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.privacy.differentialprivacy.GaussianNoise;
import com.google.privacy.differentialprivacy.TestNoiseFactory;
import com.google.privacy.differentialprivacy.proto.testing.StatisticalTests.ClosenessTestParameters;
import com.google.privacy.differentialprivacy.proto.testing.StatisticalTests.DistributionClosenessTestCase;
import com.google.privacy.differentialprivacy.proto.testing.StatisticalTests.DistributionClosenessTestCaseCollection;
import com.google.privacy.differentialprivacy.proto.testing.StatisticalTests.NoiseSamplingParameters;
import com.google.privacy.differentialprivacy.testing.ReferenceNoiseUtil;
import com.google.privacy.differentialprivacy.testing.StatisticalTestsUtil;
import com.google.privacy.differentialprivacy.testing.VotingUtil;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that the distribution of the noise generated by {@link GaussianNoise} is close to a
 * Gaussian distribution of the appropriate mean and variance.
 */
@RunWith(Parameterized.class)
public final class GaussianClosenessTest {
  private static final String TEST_CASES_FILE_PATH =

  "external/com_google_differential_privacy/proto/testing/gaussian_closeness_test_cases.textproto";

  private final DistributionClosenessTestCase testCase;

  public GaussianClosenessTest(DistributionClosenessTestCase testCase) {
    this.testCase = testCase;
  }

  @Parameterized.Parameters
  public static Iterable<?> testCases() {
    return getTestCaseCollectionFromFile().getDistributionClosenessTestCaseList();
  }

  @Test
  public void gaussianClosenessTest() {
    GaussianNoise noise = TestNoiseFactory.createGaussianNoise(new SecureRandom());
    NoiseSamplingParameters samplingParameters = testCase.getNoiseSamplingParameters();
    ClosenessTestParameters closenessTestParameters = testCase.getClosenessTestParameters();
    Supplier<Double> gaussianSampleGenerator =
        () ->
            noise.addNoise(
                samplingParameters.getRawInput(),
                samplingParameters.getL0Sensitivity(),
                samplingParameters.getLinfSensitivity(),
                samplingParameters.getEpsilon(),
                samplingParameters.getDelta());
    Supplier<Double> referenceSampleGenerator =
        () ->
            ReferenceNoiseUtil.sampleGaussian(
                closenessTestParameters.getMean(), closenessTestParameters.getVariance());

    assertThat(
            VotingUtil.runBallot(
                () ->
                    generateVote(
                        gaussianSampleGenerator,
                        referenceSampleGenerator,
                        samplingParameters.getNumberOfSamples(),
                        closenessTestParameters.getL2Tolerance(),
                        closenessTestParameters.getGranularity()),
                getNumberOfVotesFromFile()))
        .isTrue();
  }

  private static int getNumberOfVotesFromFile() {
    return getTestCaseCollectionFromFile().getVotingParameters().getNumberOfVotes();
  }

  private static DistributionClosenessTestCaseCollection getTestCaseCollectionFromFile() {
    DistributionClosenessTestCaseCollection.Builder testCaseCollectionBuilder =
        DistributionClosenessTestCaseCollection.newBuilder();
    try {
      TextFormat.merge(
          new InputStreamReader(
              GaussianClosenessTest.class
                  .getClassLoader()
                  .getResourceAsStream(TEST_CASES_FILE_PATH),
              UTF_8),
          testCaseCollectionBuilder);
    } catch (IOException e) {
      throw new RuntimeException("Unable to read input.", e);
    } catch (NullPointerException e) {
      throw new RuntimeException("Unable to find input file.", e);
    }
    return testCaseCollectionBuilder.build();
  }

  private static boolean generateVote(
      Supplier<Double> sampleGeneratorA,
      Supplier<Double> sampleGeneratorB,
      int numberOfSamples,
      double l2Tolerance,
      double granularity) {
    Double[] samplesA = new Double[numberOfSamples];
    Double[] samplesB = new Double[numberOfSamples];
    for (int i = 0; i < numberOfSamples; i++) {
      samplesA[i] = StatisticalTestsUtil.discretize(sampleGeneratorA.get(), granularity);
      samplesB[i] = StatisticalTestsUtil.discretize(sampleGeneratorB.get(), granularity);
    }
    return StatisticalTestsUtil.verifyCloseness(samplesA, samplesB, l2Tolerance);
  }
}
