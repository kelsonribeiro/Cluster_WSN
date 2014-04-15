/* BEGIN NOTICE

Copyright (c) 1992 by John Sarraille and Peter DiFalco
(john@ishi.csustan.edu)

Permission to use, copy, modify, and distribute this software
and its documentation for any purpose and without fee is hereby
granted, provided that the above copyright notice appear in all
copies and that both that copyright notice and this permission
notice appear in supporting documentation.

The algorithm used in this program was inspired by the paper
entitled "A Fast Algorithm To Determine Fractal Dimensions By
Box Counting", which was written by Liebovitch and Toth, and
which appeared in the journal "Physics Letters A", volume 141,
pp 386-390, (1989).

This program is not warranted: use at your own risk.

END NOTICE */

package projects.wsneeFD.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;

import projects.wsneeFD.nodes.nodeImplementations.SimpleNode.DataRecord;
import projects.wsneeFD.nodes.nodeImplementations.SimpleNode;

public class FD3BigInt {

	static int embed_dim; /* How many coordinates (columns) points have. */
	static int dataLines; /*
						 * Number of data points in input file (Total number of
						 * lines).
						 */
	static double pmax, pmin;
	static BigInteger[][] data; /* ARRAY OF DATA POINTS */
	static final boolean debugging = false, checking = false;
	static final int sizoflong = 8;
	static final int sizofint = 4;
	static final int numbits = (8 * sizoflong);

	// static final int numbits = (8*sizofint); // Mutually exclusive with the
	// above line
	static int mark; /* Marks smallest usable box count */

	static int boxCount[] = new int[numbits + 1]; /*
												 * Array to keep track of the
												 * box counts of each size. Each
												 * index is the base-2 log of
												 * the corresponding box size.
												 */

	static double negLogBoxCount[] = new double[numbits + 1]; /*
															 * FOR EACH BOX SIZE
															 * #s,
															 * negLogBoxCount[s]
															 * WILL EVENTUALLY
															 * BE SET TO THE
															 * NEGATIVE OF THE
															 * LOG (BASE TWO) OF
															 * THE NUMBER OF
															 * BOXES OF SIZE #s
															 * THAT ARE OCCUPIED
															 * BY ONE OR MORE
															 * DATA POINTS.
															 */

	static double logSumSqrFreq[] = new double[numbits + 1]; /*
															 * FOR EACH BOX SIZE
															 * #s,
															 * logSumSqrFreq[s]
															 * WILL EVENTUALLY
															 * BE SET TO THE LOG
															 * (BASE TWO) OF THE
															 * PROBABILITY THAT
															 * TWO RANDOMLY
															 * CHOSEN DATA
															 * POINTS ARE IN THE
															 * SAME BOX OF SIZE
															 * #s.
															 */

	static double information[] = new double[numbits + 1]; /*
															 * FOR EACH BOX SIZE
															 * #s,
															 * information[s]
															 * WILL EVENTUALLY
															 * BE SET TO THE
															 * INFORMATION (BASE
															 * TWO) IN THE
															 * DISTRIBUTION OF
															 * DATA POINTS IN
															 * THE BOXES OF SIZE
															 * #s.
															 */

	static double capDim;
	static double infDim;
	static double corrDim;
	static double slope;
	static double intercept;

	/* ################################################################## */
	/* ################################################################## */
	public static int getEmbedDim(String userInputFile) {
		int count = 0;
		/*
		 * if (debugging || checking)
		 * System.out.print("Now inside get_e_dim.\n"); if (debugging ||
		 * checking) System.out.print("About to open input file.\n");
		 */

		String filename = userInputFile;
		String fCap = filename.toUpperCase();
		if (fCap.endsWith("CSV")) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(
						filename));
				String text = reader.readLine(); // Jumps the first line (with
													// the number of file lines)

				if ((text = reader.readLine()) != null) {
					String s[] = text.split("[,\t ]");
					count = s.length;
				}
				reader.close();
			} catch (IOException e) {
				System.err
						.println("Sorry! There was a problem reading your file: ["
								+ filename + "] !");
				return -1;
			}
		}
		return (count);
	} // end getEmbedDim(String userInputFile)

	public static ArrayList<DataRecord> dataReadings(
			ArrayList<SimpleNode> clusters) {
		ArrayList<DataRecord> data = new ArrayList<DataRecord>();

		for (int i = 1; i < clusters.size(); i++) {
			for (int j = 0; j < clusters.get(i).dataRecordItens.size(); j++) {
				data.add(clusters.get(i).dataRecordItens.get(j));
			}

		}
		return data;
	}

	/* ################################################################## */
	/* ################################################################## */
	public static void maxMin(ArrayList<DataRecord> clusters) {

		double temp;

		/*
		 * if (debugging || checking) {
		 * System.out.print("Now control is in max_min.\n");
		 * System.out.print("Number of data lines is "+dataLines+" ...\n");
		 * System.out.print("Starting to read data.\n"); } // if (debugging ||
		 * checking)
		 */
		/*
		 * find maximum and minimum data values in the input file. These are
		 * needed so that the data can be scaled to be a set of non-negative
		 * integers between 0 and maxdiam, where maxdiam will be the largest
		 * integer value expressible as an element of the data type
		 * "unsigned long int".
		 */

		// numToRead = ((long) embed_dim) * dataLines;
		boolean firstData = true;
		for (int i = 0; i < clusters.size(); i++) {
			for (int j = 0; j < clusters.get(i).values.length; j++) {
				temp = clusters.get(i).values[j];

				if (firstData) {
					pmax = pmin = temp;

					firstData = false;
				} // if (firstData)
				else {
					if (temp > pmax) {
						pmax = temp;
					} // end if (temp > pmax)
					else {
						if (temp < pmin) {
							pmin = temp;
						} // end if (temp < pmin)
					} // end else from if (temp > pmax)
				}
			}

		} // end else from if (firstData)
		// end for (int j = 0; j < s.length; j++)
		// end if ((text = reader.readLine()) != null)
		// end for (int i = 1; i < dataLines; i++)

		/*
		 * check to see if the maximum equals the minimum -- this is a
		 * degenerate case -- or it might mean that the input file is faulty.
		 */
		/*
		 * if (pmax == pmin) { System.out.print
		 * ("The input file, "+userInputFile+", is confusing!\n\n");
		 * System.out.print
		 * ("Either all the points in "+userInputFile+" have the same ");
		 * System.out.print ("coordinates,\n"); System.out.print
		 * ("(THE FRACTAL DIMENSION IS ZERO IN THIS CASE.)\n\n");
		 * System.out.print
		 * ("or "+userInputFile+" is simply of the wrong form for an\n");
		 * System.out.print ("input file to this program -- please check.\n"); }
		 * /* close the input file
		 */

	}

	// end max_min(String userinputfile)
	// end max_min(String userinputfile, double pmax, double pmin)

	/* ################################################################## */
	/* ################################################################## */
	/*
	 * This procedure performs one pass of the radix sort. It assumes that the
	 * queues each have a marker in front at the time of the call, and this is
	 * the condition the procedure LEAVES the queues in when it terminates.
	 */
	public void rad_pass() {
	} // end rad_pass(int coord, int bitpos)

	/* ################################################################## */
	/* ################################################################## */
	/*
	 * THIS SORT IS TO BE USED DIRECTLY AFTER FDDRIVER DOES THE INITIAL LOADING
	 * OF THE DATA INTO THE QUEUES. IT LEAVES THE DATA ESSENTIALLY SORTED, WITH
	 * ALL THE DATA WHOSE X-COORD STARTS WITH 0 IN Q[0], IN ORDER, AND ALL THE
	 * DATA WHOSE X-COORD STARTS WITH 1 IN Q[1], IN ORDER. THUS THE SWEEP THAT
	 * COMES NEXT MUST TRAVERSE Q[0], AND THEN Q [1].
	 */
	public void radixsort() {
	} // end radixsort()

	/* ################################################################## */
	/* ################################################################## */
	/*
	 * THIS PROCEDURE TRAVERSES THE SORTED DATA POINT LIST, AND EXTRACTS THE
	 * INFORMATION NEEDED TO COMPUTE THE CAPACITY, INFORMATION, AND CORRELATION
	 * DIMENSIONS OF THE DATA. DATA IS CORRECTED ON THE FLY DURING THE
	 * TRAVERSAL, AND THEN "MASSAGED". AFTER "SWEEP" RUNS, WE NEED ONLY FIT A
	 * SLOPE TO THE DATA TO OBTAIN THE FRACTAL DIMENSIONS.
	 */
	public static void sweep() { // (long boxCountS[], double negLogBoxCountS[],
									// double logSumSqrFreqS[], double
									// informationS[]) {

		/*
		 * Here we allocate storage for a word that describes the bit changes
		 * between two different unsigned long int's. We have one of these words
		 * for each of the embedding dimensions. These will come in handy when
		 * the sweep is done that gets the box counts.
		 */

		BigInteger diff_test[] = new BigInteger[embed_dim]; /*
															 * XOR'S OF PAIRS OF
															 * COORDINATES
															 */

		int bitpos, countpos, coord; // queue_num;
		boolean found;
		int pointCount[] = new int[numbits + 1];
		int current, previous;
		double sumSqrFreq[] = new double[numbits + 1];
		double freq;
		double log2 = Math.log(2.0);

		/* INIT boxCount, pointCountS, sumSqrFreq, AND information. */
		for (countpos = 0; countpos <= numbits; countpos++) {
			boxCount[countpos] = 1;
			sumSqrFreq[countpos] = 0.0;
			pointCount[countpos] = 1;
			information[countpos] = 0.0;
		}

		previous = 0;
		// System.out.println("");
		for (int cont = 0; cont < data.length; cont++) {
			current = cont;
			found = false;
			/* START BY LOOKING AT THE BIGGEST BOX SIZE */
			bitpos = numbits - 1;
			for (int coord2 = (embed_dim - 1); coord2 >= 0; coord2--) {
				diff_test[coord2] = data[previous][coord2]
						.xor(data[current][coord2]); // data[previous][coord2] ^
														// data[current][coord2];
				// System.out.println("data["+previous+"]["+coord2+"] XOR data["+current+"]["+coord2+"] = "+diff_test[coord2]);
			}
			// System.out.println("");
			do {
				coord = (embed_dim - 1);
				do {
					/*
					 * IF THE CURRENT POINT AND PREVIOUS POINTS ARE IN DIFFERENT
					 * BOXES OF THIS SIZE,
					 */
					if (FDRadixSort.queueNo(diff_test[coord], bitpos) == 1) { // (
																				// IS_A_ONE(diff_test[coord],bitpos)
																				// )

						// System.out.println("cont = "+cont+", diff_test[coord] = "+diff_test[coord]+", bitpos = "+bitpos);

						/*
						 * THEN THE CURRENT POINT IS IN NEW BOXES OF ALL SMALLER
						 * SIZES TOO, AND STILL IN THE SAME BOXES OF LARGER
						 * SIZES, SO ...
						 */
						for (countpos = bitpos; countpos >= 0; countpos--) {
							/*
							 * CALCULATE FREQUENCY OF POINTS IN THE BOX,
							 * ASSUMING FOR NOW THAT THE NUMBER OF DATA LINES IN
							 * THE INPUT FILE IS THE NUMBER OF DISTINCT POINTS
							 * IN THE DATA SET. WE ADJUST THIS AT THE END OF
							 * THIS FUNCTION.
							 */
							if (debugging) {
								System.out.println("pointCount[" + countpos
										+ "] is " + pointCount[countpos]
										+ "...\n");
							}
							freq = pointCount[countpos] / (double) dataLines;

							/*
							 * WE WILL ENCOUNTER NO MORE OF THE POINTS IN THE
							 * BOX WE JUST LEFT (THE SPECIAL ORDERING OF THE
							 * SORT WE USED ABOVE GUARANTEES THIS!), SO WE
							 * COMPUTE WHAT THIS BOX CONTRIBUTES TO THE RUNNING
							 * SUMS.
							 */
							sumSqrFreq[countpos] += (freq * freq);
							information[countpos] += (freq * Math.log(freq) / log2);

							/*
							 * WE HAVE GOTTEN INTO A NEW BOX AT THIS LEVEL, SO
							 * WE REFLECT THE NEW BOX IN THE COUNT
							 */
							boxCount[countpos]++;

							/*
							 * SINCE WE HAVE A NEW BOX AT THIS LEVEL, THERE IS
							 * ONLY ONE KNOWN POINT IN IT SO FAR -- THE CURRENT
							 * POINT
							 */
							pointCount[countpos] = 1;
						}
						for (countpos = bitpos + 1; countpos <= numbits; countpos++)
							/*
							 * THE CURRENT POINT IS IN THE BOXES AT THESE
							 * LEVELS, SO JUST INCREMENT THE POINT COUNTER.
							 */
							pointCount[countpos]++;
						found = true;
					} else {
						coord--;
					}
				} while ((!found) && (coord > -1));
				bitpos--;
			} while ((!found) && (bitpos > -1));
			previous = current;
		}

		/*
		 * NOW ADD IN THE CONTRIBUTION DUE TO THE COUNTS REMAINING AFTER THE
		 * LAST POINT HAS BEEN FOUND, RENORMALIZE WITH BOXCOUNT[0], AND MASSAGE
		 * THE RAW DATA FROM THE TRAVERSAL SO THAT IS IS READY FOR THE LEAST
		 * SQUARES FITTING.
		 */

		for (countpos = numbits; countpos >= 0; countpos--) {
			negLogBoxCount[countpos] = -Math.log((double) boxCount[countpos])
					/ log2;

			if (debugging) {
				System.out.println("pointCount[" + countpos + "] is "
						+ pointCount[countpos] + "...\n");
			}
			freq = pointCount[countpos] / (double) dataLines;

			sumSqrFreq[countpos] += (freq * freq);
			sumSqrFreq[countpos] *= (dataLines / (double) boxCount[0]);
			sumSqrFreq[countpos] *= (dataLines / (double) boxCount[0]);

			/*
			 * sumSqrFreq[countpos] NOW CONTAINS THE SUM OF THE SQUARES OF THE
			 * FREQUENCIES OF POINTS IN ALL OCCUPIED BOXES OF THE SIZE
			 * CORRESPONDING TO countpos.
			 */

			logSumSqrFreq[countpos] = Math.log(sumSqrFreq[countpos]) / log2;
			information[countpos] += (freq * Math.log(freq) / log2);
			information[countpos] *= (dataLines / (double) boxCount[0]);
			information[countpos] += (Math.log((double) dataLines) - Math
					.log((double) boxCount[0])) / log2;

			/*
			 * information[countpos] NOW CONTAINS THE INFORMATION SUM FOR ALL
			 * THE OCCUPIED BOXES OF THIS SIZE.
			 */

		} // end for (countpos = numbits; countpos >= 0; countpos--)
	} // end public static void sweep()

	/* ################################################################## */
	/* ################################################################## */
	/*
	 * MARK GREATEST INDEX WHERE COUNT > boxCountF[0]/cutOff_factor.
	 * 
	 * COUNTS AT LESSER INDEXES WILL NOT BE USED IN THE ESTIMATE OF FRACTAL
	 * DIMENSION -- DISTORTION DUE TO SATURATION IS THE CONCERN.
	 * 
	 * NOTE THAT boxCountF[0] IS THE NUMBER OF BOXES OF SIZE 1 (THE SMALLEST
	 * SIZE) THAT CONTAIN A POINT OF THE SET. FOR ALL PRACTICAL PURPOSES,
	 * boxCountF[0] WILL EQUAL THE NUMBER OF DISTINCT POINTS IN THE INPUT FILE,
	 * BECAUSE THESE BOXES ARE REALLY SMALL COMPARED TO THE SIZE OF THE BIGGEST
	 * BOX (ABOUT 4 BILLION IF AN UNSIGNED LONG INT IS 32 BITS TO THE PLATFORM
	 * COMPILER. THE POINTS ARE SCALED BY THE PROGRAM SO THAT THE SET IS TOO
	 * "LARGE" TO FIT IN THE NEXT SMALLEST BOX SIZE, SO THAT "1" IS THE SMALLEST
	 * DIFFERENCE IN VALUE THAT CAN BE RESOLVED.) ONE BOX, IN EFFECT, COVERS
	 * ONLY A SINGLE POINT OF THE INPUT SET BECAUSE THE PROGRAM CAN'T RESOLVE
	 * POINTS WITH A SMALLER DIFFERENCE.
	 * 
	 * WE THINK IT WOULD BE A BAD IDEA TO USE dataLines/cutOff_factor AS THE
	 * LIMIT BECAUSE IN CASES WHERE THERE WERE MANY DUPLICATE POINTS, WE WOULD
	 * SERIOUSLY OVER-ESTIMATE THE NUMBER OF DISTINCT POINTS, AND THUS USE
	 * SATURATED DATA TO BASE THE ESTIMATE OF FRACTAL DIMENSION UPON. WHEN
	 * TESTING THE PROGRAM WITH RANDOM DATA SAMPLED WITH REPLACEMENT, THIS COULD
	 * THROW THE RESULTS WAY OFF. (THIS HAPPENED TO US, AND IT TOOK US A WHILE
	 * TO FIGURE OUT WHY. AFTERWARDS, WE STOPPED USING dataLines/cutOff_factor,
	 * AND CHANGED TO boxCountF[0]/cutOff_factor.)
	 */
	public static void findMark() // (int markPtr, int boxCountM[])
	// ulong *markPtr, boxCountM[numbits+1] ;
	{
		int i, cutOff_factor = 1;

		/* Calculate cutOff_factor = 2^(embed_dim) + 1 */
		for (i = 1; i <= embed_dim; i++) {
			cutOff_factor = cutOff_factor * 2;
		} // end for (i = 1; i <= embed_dim; i++)
		cutOff_factor++;

		mark = 0;
		for (i = 0; i < numbits; i++) {
			if (boxCount[i] > boxCount[0] / cutOff_factor) {
				mark = i;
				// System.out.println("i = "+i+", boxCount[i] = "+boxCount[i]+", boxCount[0] = "+boxCount[0]+", cutOff_factor = "+cutOff_factor+"\n");
			} // end if (boxCountM[i] > boxCountM[0]/cutOff_factor)
		} // end for (i = 0; i < numbits; i++)
	} // end findMark(int markPtr, int boxCountM[])

	/* ################################################################## */
	/* ################################################################## */
	public static void GetDims() /*
								 * (double negLogBoxCountF[], double
								 * logSumSqrFreqF[], double informationF[],
								 * ulong markF, double *capDimPtr, double
								 * *infDimPtr, double *corrDimPtr)
								 */
	// ulong markF ;
	// double negLogBoxCountF[numbits+1], informationF[numbits+1],
	// logSumSqrFreqF[numbits+1],
	// *capDimPtr, *infDimPtr, *corrDimPtr;
	{
		int i;
		double logEps[] = new double[numbits + 1];
		// double slope;
		// double intercept;

		/* GET LOG (BASE 2) OF THE DIAMETER OF THE I'TH SIZE OF BOX. */
		for (i = numbits; i >= 0; i--) {
			logEps[i] = i;
		}

		/*
		 * fitLSqrLine (markF, numbits-4, logEps, negLogBoxCountF,
		 * &slope,&intercept);
		 */
		fitLSqrLine(mark, numbits - 2, logEps, negLogBoxCount);
		capDim = slope;
		/*
		 * fitLSqrLine(markF, numbits-4, logEps, informationF, &slope,
		 * &intercept);
		 */
		fitLSqrLine(mark, numbits - 2, logEps, information);
		infDim = slope;
		/*
		 * fitLSqrLine(markF,numbits-4, logEps, logSumSqrFreqF, &slope,
		 * &intercept);
		 */
		fitLSqrLine(mark, numbits - 2, logEps, logSumSqrFreq);
		corrDim = slope;
	}

	/* ################################################################## */
	/* ################################################################## */
	/*
	 * FIT LEAST SQUARE LINE TO DATA IN X,Y. NO PROTECTION AGAINST OVERFLOW
	 * HERE. IT IS ASSUMED THAT LAST > FIRST AND THAT THE X'S ARE NOT ALL THE
	 * SAME -- ELSE DIVISION BY ZERO WILL OCCUR.
	 */
	public static void fitLSqrLine(int first, int last, double X[], double Y[]) {
		int index, pointCount;
		double Xsum = 0, Ysum = 0, XYsum = 0, XXsum = 0, Xmean = 0, Ymean = 0, Xtemp, Ytemp;
		for (index = first; index <= last; index++) {
			Xtemp = X[index];
			Ytemp = Y[index];
			Xsum += Xtemp;
			Ysum += Ytemp;
			XYsum += (Xtemp * Ytemp);
			XXsum += (Xtemp * Xtemp);
		}
		pointCount = last - first + 1;
		Xmean = Xsum / pointCount;
		Ymean = Ysum / pointCount;
		slope = (XYsum - Xsum * Ymean) / (XXsum - Xsum * Xmean);
		intercept = Ymean - slope * Xmean;
	} // end fitLSqrLine (int first, int last, double X[], double Y[])

	public static void printData(BigInteger[][] dat) {
		System.out.println("Begin printing ...");
		for (int i = 0; i < dataLines; i++) {
			for (int j = 0; j < embed_dim; j++) {
				System.out.print(data[i][j] + " ");
			}
			System.out.println();
		}
		System.out.println("... End");
	} // end printData(BigInteger[][] dat)

	/* ################################################################## */
	/* ################################################################## */

	public static double calculatesFractalDimensions(ArrayList<SimpleNode> clusters) {

		ArrayList<DataRecord> datum = dataReadings(clusters);
		// Long maxdiam = new Long(0); /* 2^numbits - 1: determines the scaling.
		// */
		// BigInteger maxDiam = new BigInteger("18446744073709551614"); // For
		// compatibility with C language
		BigInteger maxDiam; /* 2^numbits - 1: determines the scaling. */
		double max, min; /*
						 * Maximum and minimum values in input file-- we need to
						 * know these in order to scale the input points.
						 */
		double buf; /*
					 * A buffer for inputs to stay in until they are scaled and
					 * converted to integers.
					 */

		/* FIND OUT HOW MANY COORDINATES POINTS HAVE -- 1?, 2?, 3?, MORE? */
		embed_dim = ((SimpleNode.DataRecord) clusters.get(0).dataRecordItens
				.get(0)).values.length;

		maxMin(datum);
		max = pmax;
		min = pmin;

		BigInteger big2 = new BigInteger("2");
		BigInteger big1 = new BigInteger("1");
		maxDiam = (big2.pow(numbits)).subtract(big1);

		data = new BigInteger[datum.size()][embed_dim];

		for (int i = 0; i < datum.size(); i++) {

			for (int j = 0; j < embed_dim; j++) { // for (j = 0; j < s.length;
													// j++)

				buf = datum.get(i).values[j];
				BigDecimal bigMax = new BigDecimal(max + "");
				BigDecimal bigMin = new BigDecimal(min + "");
				BigDecimal bigBuf = new BigDecimal(buf + "");
				BigDecimal bigMaxDiam = new BigDecimal(maxDiam);
				/*
				 * String temp = ""+((int)(max-min)); String temp2 =
				 * ""+((int)(buf-min));
				 */
				data[i][j] = (bigBuf.subtract(bigMin).multiply((bigMaxDiam
						.divide(bigMax.subtract(bigMin), 2,
								RoundingMode.HALF_UP)))).toBigInteger();

				// BigInteger bigMaxDiamInt = bigMaxDiam.toBigInteger();
				BigInteger lessBig = data[i][j].min(maxDiam);

				if (lessBig.equals(maxDiam)) {
					data[i][j] = data[i][j].subtract(maxDiam);
				}
			}
		}

		/*
		 * Get started on the radix sort by putting the data in the queue
		 * corresponding to the least significant bit of the last coordinate.
		 */

		/* radix sort queues */

		data = FDRadixSort.radixSort2(data);

		/* sweep data */

		// TODO: Convert / create sweep method to Java
		// sweep(boxCount, negLogBoxCount, logSumSqrFreq, information);
		sweep();

		// TODO: Convert findMark method to Java
		// findMark(&mark, boxCount) ;
		findMark();

		/* GET RID OF THIS LINE WHEN DONE WITH TEST!!! */
		/* mark = 24; */

		// GetDims(negLogBoxCount, logSumSqrFreq, information, mark, &capDim,
		// &infDim, &corrDim);
		GetDims();

		return capDim;
	}

	
} // end main(String args[])
