/*******************************************************************************
 * Copyright (C) 2017 Chang Wei Tan
 * 
 * This file is part of TSI.
 * 
 * TSI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * TSI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with TSI.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import classifiers.*;
import datasets.Dataset;
import tools.*;
import tree.TreeNode;

public class UCR_TSI {
	/* L_UCR_TSI
	 * This is a class for TSI Experiment on UCR datasets
	 * 
	 * Last modified: 14/01/2017
	 */
	public static void main (String[] args) {
		final Tools tools = new Tools();
		String projectPath = "";
		String datasetName = "ArrowHead";	
		int K = 6;
		int L = 5;
		int Imax = 10;
		int k = 1;			
		int w = 0;	
		int testNum = 10;
		int step = 1;
		int nbPre = 3;
		
		if (args.length > 0) {projectPath = args[0] + "/";}
		if (args.length > 1) {datasetName = args[1];}
		if (args.length > 2) {K = Integer.parseInt(args[2]);}
		if (args.length > 3) {Imax = Integer.parseInt(args[3]);}
		if (args.length > 4) {k = Integer.parseInt(args[4]);}
		if (args.length > 5) {testNum = Integer.parseInt(args[5]);}
		if (args.length > 6) {step = Integer.parseInt(args[6]);}
		if (args.length > 7) {nbPre = Integer.parseInt(args[7]);}
		
		final Dataset UCR = new Dataset(datasetName);		// tools to read and load UCR dataset
		UCR.loadTestSet(projectPath + "dataset/UCR_Time_Series_Archive/");
		UCR.loadTrainSet(projectPath + "dataset/UCR_Time_Series_Archive/");
		w = UCR.window();
		L = UCR.trainSize();
		
		if (args.length > 8) {L = Integer.parseInt(args[8]);}
		if (args.length > 9) {w = Integer.parseInt(args[9]);}
		
		int stepSize = 0;
		if (step != 1 && L != 1) {stepSize = (int) Math.ceil((double) UCR.trainSize()/step) + 1;}
		else {stepSize = (int) Math.ceil((double) UCR.trainSize()/step);}
		
		final double[][] preError = new double[testNum][nbPre];
		final double[][] preTime = new double[testNum][nbPre];
		final double[][] preDist = new double[testNum][nbPre];
		
		final double[][] averagePrecisionPerQuery = new double[testNum][stepSize];
		final double[][] averageErrorPerQuery = new double[testNum][stepSize];
		final double[][] averageTimePerQuery = new double[testNum][stepSize];
		final double[][] averageDistPerQuery = new double[testNum][stepSize];
		int[] seenSoFar = new int[stepSize];
		int minPre = UCR.testSize();
		
		for (int i = 0; i < testNum; i++) {
			ArrayList<double[]> trainingDataset = UCR.TrainSet();
			ArrayList<Integer> trainingDatasetClass = UCR.ClassTrain();
			ArrayList<Integer> trainingDatasetIndex = UCR.IndexTrain();
			final int[] trainingRandomIndex = UCR.randomize(trainingDataset, trainingDatasetClass, trainingDatasetIndex);
			trainingDataset = UCR.extractDataset(trainingDataset, trainingRandomIndex);
			trainingDatasetClass = UCR.extractLabel(trainingDatasetClass, trainingRandomIndex);
			
			ArrayList<double[]> testingDataset = UCR.TestSet();
			ArrayList<Integer> testingDatasetClass = UCR.ClassTest();
			final ArrayList<Integer> testingDatasetIndex = UCR.IndexTest();
			final int[] testingRandomIndex = UCR.randomize(testingDataset, testingDatasetClass, testingDatasetIndex);
			testingDataset = UCR.extractDataset(testingDataset, testingRandomIndex);
			testingDatasetClass = UCR.extractLabel(testingDatasetClass, testingRandomIndex);
			
			final int[] tempIndex = UCR.read1NNIndex(projectPath + "index1NN/UCR/");
			final int[] actual1NNIndex = tools.mapRandArray(tempIndex, testingRandomIndex, trainingRandomIndex);
			
			TreeNode root = new TreeNode();
			root.setRoot();
			final ClassifierTSI classifier = new ClassifierTSI(trainingDataset, trainingDatasetClass, K, k, L, step, stepSize, nbPre);
			final long startTime = System.nanoTime();
			root = classifier.buildTree(root, trainingDataset, trainingDatasetClass, trainingDatasetIndex, Imax, w);
			final double buildTime = (double)( (System.nanoTime() - startTime)/1e9);
			System.out.println("Build time: " + buildTime);
			final double errorRate = classifier.performance(root, testingDataset, testingDatasetClass, w, actual1NNIndex);
			final double averageTime = classifier.getAverageQueryTime();
			final double precision = classifier.getPrecision();
			final double distComputations = classifier.getDistComputation();
			
			averagePrecisionPerQuery[i] = classifier.getAveragePrecisionPerQuery();
			averageErrorPerQuery[i] = classifier.getAverageErrorPerQuery();
			averageTimePerQuery[i] = classifier.getAverageTimePerQuery();
			averageDistPerQuery[i] = classifier.getAverageDistPerQuery();
			seenSoFar = classifier.getSeenSoFarPerQuery();
			
			preError[i] = classifier.getPreError();
			preTime[i] = classifier.getPreTime();
			preDist[i] = classifier.getPreDist();
			minPre = Math.min(minPre, preTime[i].length);
			
			System.out.println((i+1) + ", Error rate: " + errorRate + ", Average Query Time is: " + averageTime + ", Average Distance Computations: " + distComputations + ", Precision: " + precision);
		}
		final String csvFile = projectPath + "outputs/experiments/" + datasetName + "_TSI_K" + K + ".csv";
		writeToCsv(csvFile, datasetName, minPre, preError, preTime, preDist, averageErrorPerQuery, averagePrecisionPerQuery, averageTimePerQuery, averageDistPerQuery, seenSoFar);
	}
	
	private final static void writeToCsv(final String csvFile, final String datasetName,  final int minPre,
			final double[][] preError, final double[][] preTime, 
			final double[][] preDist, final double[][] error, 
			final double[][] precision, final double[][] time, 
			final double[][] dist, final int[] seen){
		try	{
			FileWriter writer = new FileWriter(csvFile);
			writer.append(datasetName + '\n');
			writer.append("Runs,Error rate" + '\n');
			writer.append("Seen,");
			for (int i = 1; i <= minPre; i++)
				writer.append("-" + i + ",");
			writer.append(seen[0] + ",");
			for (int i = 1; i < seen.length; i++)
				writer.append(Integer.toString(seen[i]) + ",");
			writer.append('\n');
			for (int i = 0; i < preError.length; i++) {
				writer.append(Integer.toString(i+1) + "," + Double.toString(preError[i][0]));
				for (int j = 1; j < minPre; j++)
					writer.append("," + Double.toString(preError[i][j]));
				
				writer.append("," + error[i][0]);
				for (int j = 1; j < error[0].length; j++)
					writer.append("," + Double.toString(error[i][j]));
				writer.append('\n');
			}
			writer.append('\n');
			
			writer.append("Runs,Precision" + '\n');
			writer.append("Seen,");
			for (int i = 1; i <= minPre; i++)
				writer.append("-" + i + ",");
			writer.append(seen[0] + ",");
			for (int i = 1; i < seen.length; i++)
				writer.append(Integer.toString(seen[i]) + ",");
			writer.append('\n');
			for (int i = 0; i < preError.length; i++) {
				writer.append(Integer.toString(i+1) + ",0");
				for (int j = 1; j < minPre; j++)
					writer.append("," + Double.toString(0));
				
				writer.append("," + precision[i][0]);
				for (int j = 1; j < precision[0].length; j++)
					writer.append("," + Double.toString(precision[i][j]));
				writer.append('\n');
			}
			writer.append('\n');
			
			writer.append("Runs,Time" + '\n');
			writer.append("Seen,");
			for (int i = 1; i <= minPre; i++)
				writer.append("-" + i + ",");
			writer.append(seen[0] + ",");
			for (int i = 1; i < seen.length; i++)
				writer.append(Integer.toString(seen[i]) + ",");
			writer.append('\n');
			for (int i = 0; i < preError.length; i++) {
				writer.append(Integer.toString(i+1) + "," + Double.toString(preTime[i][0]));
				for (int j = 1; j < minPre; j++)
					writer.append("," + Double.toString(preTime[i][j]));
				
				writer.append("," + time[i][0]);
				for (int j = 1; j < time[0].length; j++)
					writer.append("," + Double.toString(time[i][j]));
				writer.append('\n');
			}
			writer.append('\n');
			
			writer.append("Runs,Distance" + '\n');
			writer.append("Seen,");
			for (int i = 1; i <= minPre; i++)
				writer.append("-" + i + ",");
			writer.append(seen[0] + ",");
			for (int i = 1; i < seen.length; i++)
				writer.append(Integer.toString(seen[i]) + ",");
			writer.append('\n');
			for (int i = 0; i < preError.length; i++) {
				writer.append(Integer.toString(i+1) + "," + Double.toString(preDist[i][0]));
				for (int j = 1; j < minPre; j++)
					writer.append("," + Double.toString(preDist[i][j]));
				
				writer.append("," + dist[i][0]);
				for (int j = 1; j < dist[0].length; j++)
					writer.append("," + Double.toString(dist[i][j]));
				writer.append('\n');
			}
			writer.append('\n');
			
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
