package net.librec.recommender.cf.ranking;

import com.google.common.collect.BiMap;
import net.librec.annotation.ModelData;
import net.librec.common.LibrecException;
import net.librec.data.DataModel;
import net.librec.math.structure.*;
import net.librec.recommender.AbstractRecommender;
import net.librec.util.Lists;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * This implementation is based on the method proposed by
 * Burke, Robin, Nasim Sonboli, Aldo Ordonez-Gauger, <strong>Balanced neighborhoods for multi-sided fairness in recommendation.</strong> FAT* 2018.
 * and
 * Xia Ning and George Karypis, <strong>SLIM: Sparse Linear Methods for Top-N Recommender Systems</strong>, ICDM 2011. <br>
 *
 * @author Nasim Sonboli
 */

@ModelData({"isRanking", "slim", "coefficientMatrix", "trainMatrix", "similarityMatrix", "knn"})
public class BLNSLIMRecommender extends AbstractRecommender {
    /**
     * the number of iterations
     */
    protected int numIterations;

    /**
     * W in original paper, a sparse matrix of aggregation coefficients
     */
    private DenseMatrix coefficientMatrix;

    /**
     * item's nearest neighbors for kNN > 0
     */
    private Set<Integer>[] itemNNs;

    /**
     * regularization parameters for the L1 or L2 term
     */
    private float regL1Norm, regL2Norm;

    /**
     *This parameter controls the influence of item balance calculation on the overall optimization.
     */
    private float Lambda3;

    /**
     * This vector is a 1 x M vector, and M is the number of users,
     * this vector is filled with either 1 or -1,
     * If a user belongs to the protected group it is +1, otherwise it is -1
     */
    private int[] groupMembershipVector;

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix itemFeatureMatrix;

    /**
     * the protected feature e.g. female
     */
    private String protectedAttribute;

    /**
     * feature id mapping
     */
    BiMap<String, Integer> featureIdMapping;

    /**
     * regression weights
     */
    private double weights;

    /**
     * number of nearest neighbors
     */
    protected static int knn;

    /**
     * item similarity matrix
     */
    private SymmMatrix similarityMatrix;

    /**
     * items's nearest neighbors for kNN <= 0, i.e., all other items
     */
    private Set<Integer> allItems;


    /**
     * initialization
     *
     * @throws LibrecException if error occurs
     */
    @Override
    protected void setup() throws LibrecException {
        super.setup();
        knn = conf.getInt("rec.neighbors.knn.number", 50);
        numIterations = conf.getInt("rec.iterator.maximum");
        regL1Norm = conf.getFloat("rec.slim.regularization.l1", 1.0f);
        regL2Norm = conf.getFloat("rec.slim.regularization.l2", 1.0f);
        Lambda3 = conf.getFloat("rec.slim.regularization.balance.controller", 1.0f);
        protectedAttribute = conf.get("data.protected.feature"); // no default value is set here.


        System.out.println("***");
        System.out.println("l1 reg: " + regL1Norm);
        System.out.println("l2 reg: " + regL2Norm);
        System.out.println("balance controller: " + Lambda3);
        System.out.println("***");

        coefficientMatrix = new DenseMatrix(numItems, numItems);
        // initial guesses: make smaller guesses (e.g., W.init(0.01)) to speed up the training
        coefficientMatrix.init();
        similarityMatrix = context.getSimilarity().getSimilarityMatrix();


        for(int itemIdx = 0; itemIdx < this.numItems; ++itemIdx) {
            this.coefficientMatrix.set(itemIdx, itemIdx, 0.0d);
        } //iterate through all of the items , initialize

        //create the nn matrix
        createItemNNs();

        // generating the groupMembershipVector from itemFeatureMatrix
        itemFeatureMatrix = getDataModel().getFeatureAppender().getItemFeatures();
        featureIdMapping = getDataModel().getFeatureAppender().getItemFeatureMap();
        BiMap<Integer, String> itemMappingInverse = itemMappingData.inverse();


        // the membership is either 1 or -1, 1 for the protected group and -1 for the unprotected
//        int itemMembership = -1; //unprotected
        groupMembershipVector = new int[numItems];

        for (int itemIdx = 0; itemIdx < numItems; itemIdx++) {
            int itemId = Integer.parseInt(itemMappingInverse.get(itemIdx)); // convert String to Integer

            int itemMembership = -1; //unprotected
            if (itemFeatureMatrix.getColumnsSet(itemIdx).size() > 0) {
                if (itemFeatureMatrix.get(itemIdx, featureIdMapping.get(protectedAttribute)) == 1) {
                    itemMembership = 1; //protected
                }
            }
            groupMembershipVector[itemIdx] = itemMembership;
        }
    }

    /**
     * train model
     *
     * @throws LibrecException if error occurs
     */
    @Override
    protected void trainModel() throws LibrecException {
        // number of iteration cycles
        for (int iter = 1; iter <= numIterations; iter++) {

            loss = 0.0d;
            weights = 0.0d; // weights

            // each cycle iterates through one coordinate direction
            for (int itemIdx = 0; itemIdx < numItems; itemIdx++) {
                // find k-nearest neighbors of each item
                Set<Integer> nearestNeighborCollection = knn > 0 ? itemNNs[itemIdx] : allItems;

                //all the ratings for itemIdx from all the users
                double[] userRatingEntries = new double[numUsers];

                Iterator<VectorEntry> userItr = trainMatrix.rowIterator(itemIdx); //look for the ratings of all users for one item
                while (userItr.hasNext()) {
                    VectorEntry userRatingEntry = userItr.next();
                    userRatingEntries[userRatingEntry.index()] = userRatingEntry.get();
                }

                // for each nearest neighbor nearestNeighborItemIdx, update coefficient Matrix by the coordinate
                // descent update rule
                for (Integer nearestNeighborItemIdx : nearestNeighborCollection) { //user nearest neighbors
                    if (nearestNeighborItemIdx != itemIdx) {
                        double gradSum = 0.0d, rateSum = 0.0d, errors = 0.0d, itemBalanceSumSqr =0.0d, itemBalanceSum =0.0d;

                        //ratings of each item for all the other users
                        Iterator<VectorEntry> nnUserRatingItr = trainMatrix.rowIterator(nearestNeighborItemIdx);
                        if (!nnUserRatingItr.hasNext()) {
                            continue;
                        }

                        int nnCount = 0;

                        while (nnUserRatingItr.hasNext()) { // now go through the ratings of a user
                            VectorEntry nnUserVectorEntry = nnUserRatingItr.next();
                            int nnUserIdx = nnUserVectorEntry.index();
                            double nnRating = nnUserVectorEntry.get();
                            double rating = userRatingEntries[nnUserIdx]; //get the rating of the nn user on the main item
                            double error = rating - predict(nnUserIdx, itemIdx, nearestNeighborItemIdx);

                            // Calculating Sigma(pk . wik)
                            double itembalance = balancePredictor(nnUserIdx, itemIdx, nearestNeighborItemIdx);

                            itemBalanceSumSqr += itembalance * itembalance; //item balance squared
                            itemBalanceSum += itembalance;
                            gradSum += nnRating * error; //
                            rateSum += nnRating * nnRating; // sigma r^2

                            errors += error * error;
                            nnCount++;
                        }

                        itemBalanceSumSqr /= nnCount;
                        itemBalanceSum /= nnCount;
                        gradSum /= nnCount;
                        rateSum /= nnCount;

                        errors /= nnCount;


                        double coefficient = coefficientMatrix.get(nearestNeighborItemIdx, itemIdx);
                        Integer itemMembership = groupMembershipVector[itemIdx];
                        // nnMembership or itemMembership?
                        loss += 0.5 * errors + 0.5 * regL2Norm * coefficient * coefficient + regL1Norm * coefficient +
                                0.5 * Lambda3 * itemBalanceSumSqr ;


                        weights += itemBalanceSum; // weights

                        /** Implementing Soft Thresholding => S(beta, Lambda1)+
                         * beta = Sigma(r - Sigma(wr)) + Lambda3 * p * Sigma(wp)
                         * & Sigma(r - Sigma(wr)) = gradSum
                         * & nnMembership = p
                         * & Sigma(wp) = itemBalanceSum
                         */

                        double beta = gradSum + (Lambda3 * itemMembership * itemBalanceSum) ; //adding item balance to the gradsum
                        double update = 0.0d; //weight

                        if (regL1Norm < Math.abs(beta)) {
                            if (beta > 0) {
                                update = (beta - regL1Norm) / (regL2Norm + rateSum + Lambda3);
                            } else {
                                // One doubt: in this case, wij<0, however, the
                                // paper says wij>=0. How to gaurantee that?
                                update = (beta + regL1Norm) / (regL2Norm + rateSum + Lambda3);
                            }
                        }

                        coefficientMatrix.set(nearestNeighborItemIdx, itemIdx, update); //update the coefficient
                    }
                }
            }

            if (isConverged(iter) && earlyStop) {
                break;
            }
        }
    }

    /**
     * predict a specific ranking score for user userIdx on item itemIdx.
     *
     * @param userIdx         user index
     * @param itemIdx         item index
     * @param excludedItemIdx excluded item index
     * @return a prediction without the contribution of excluded item
     */
    protected double predict(int userIdx, int itemIdx, int excludedItemIdx) {

        double predictRating = 0;
        Iterator<VectorEntry> itemEntryIterator = trainMatrix.colIterator(userIdx);

        while (itemEntryIterator.hasNext()) {
            VectorEntry itemEntry = itemEntryIterator.next();
            int nearestNeighborItemIdx = itemEntry.index();
            double nearestNeighborPredictRating = itemEntry.get();

            if (itemNNs[itemIdx].contains(nearestNeighborItemIdx) && nearestNeighborItemIdx != excludedItemIdx) {

                double coeff = coefficientMatrix.get(nearestNeighborItemIdx, itemIdx);
                predictRating += nearestNeighborPredictRating * coeff;
            }
        }
        return predictRating;
    }
//    /**

//     */

    /**
     * calculate the balance for each item according to their membership weight and their coefficient
     *  diag(PW) ^ 2
     *  for all of the nnItems of an item
     *
     * @param userIdx
     * @param itemIdx
     * @param excludedItemIdx
     * @return predictBalance
     */
    protected double balancePredictor(int userIdx, int itemIdx, int excludedItemIdx) {

        double predictBalance = 0;
        Iterator<VectorEntry> itemEntryIterator = trainMatrix.colIterator(userIdx);

        while (itemEntryIterator.hasNext()) {
            //iterate through the nearest neighbors of an item and calculate the prediction accordingly
            VectorEntry itemEntry = itemEntryIterator.next();
            int nearestNeighborItemIdx = itemEntry.index(); //nn item index


            if (itemNNs[itemIdx].contains(nearestNeighborItemIdx) && nearestNeighborItemIdx != excludedItemIdx) {
                //take p vector, multiply by the coefficients of neighbors (dot product)
                predictBalance += groupMembershipVector[nearestNeighborItemIdx] * coefficientMatrix.get(nearestNeighborItemIdx, itemIdx);
            }
        }
        return predictBalance;
    }


    @Override
    protected boolean isConverged(int iter) {
        double delta_loss = lastLoss - loss;
        lastLoss = loss;

        // print out debug info
        if (verbose) {
            String recName = getClass().getSimpleName().toString();
            String info = recName + " iter " + iter + ": loss = " + loss + ", delta_loss = " + delta_loss;
            LOG.info(info);
            LOG.info("The item balance sum is " + weights + "\n");
        }

        return iter > 1 ? delta_loss < 1e-5 : false;
    }


    /**
     * predict a specific ranking score for user userIdx on item itemIdx.
     *
     * @param userIdx user index
     * @param itemIdx item index
     * @return predictive ranking score for user userIdx on item itemIdx
     * @throws LibrecException if error occurs
     */
    @Override
    protected double predict(int userIdx, int itemIdx) throws LibrecException {
//        create item knn list if not exists,  for local offline model
        if (!(null != itemNNs && itemNNs.length > 0)) {
            createItemNNs();
        }
        return predict(userIdx, itemIdx, -1);
    }


    /**
     * Create item KNN list.
     */
    public void createItemNNs() {
        itemNNs = new HashSet[numItems];

        // find the nearest neighbors for each item based on item similarity
        List<Map.Entry<Integer, Double>> tempItemSimList;
        if (knn > 0) {
            for (int itemIdx = 0; itemIdx < numItems; ++itemIdx) {
                SparseVector similarityVector = similarityMatrix.row(itemIdx);
                if (knn < similarityVector.size()) {
                    tempItemSimList = new ArrayList<>(similarityVector.size() + 1);
                    Iterator<VectorEntry> simItr = similarityVector.iterator();
                    while (simItr.hasNext()) {
                        VectorEntry simVectorEntry = simItr.next();
                        tempItemSimList.add(new AbstractMap.SimpleImmutableEntry<>(simVectorEntry.index(), simVectorEntry.get()));
                    }
                    tempItemSimList = Lists.sortListTopK(tempItemSimList, true, knn);
                    itemNNs[itemIdx] = new HashSet<>((int) (tempItemSimList.size() / 0.5));
                    for (Map.Entry<Integer, Double> tempItemSimEntry : tempItemSimList) {
                        itemNNs[itemIdx].add(tempItemSimEntry.getKey());
                    }
                } else {
                    itemNNs[itemIdx] = similarityVector.getIndexSet();
                }
            }
        } else {
            allItems = new HashSet<>(trainMatrix.columns());
        }
    }
}
