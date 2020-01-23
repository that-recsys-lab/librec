package net.librec.recommender.cf.ranking;

import com.google.common.collect.BiMap;
import net.librec.annotation.ModelData;
import net.librec.common.LibrecException;
import net.librec.math.structure.*;
import net.librec.recommender.AbstractRecommender;
import net.librec.util.Lists;

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
public class UBLNSLIMRecommender extends AbstractRecommender {
    /**
     * the number of iterations
     */
    protected int numIterations;

    /**
     * W in original paper, a sparse matrix of aggregation coefficients
     */
    private DenseMatrix coefficientMatrix;

    /**
     * user's nearest neighbors for kNN > 0
     */
    private Set<Integer>[] userNNs;

    /**
     * regularization parameters for the L1 or L2 term
     */
    private float regL1Norm, regL2Norm;

    /**
     *This parameter controls the influence of item balance calculation on the overall optimization.
     */
    private float lambda3;

    /**
     * This vector is a 1 x M vector, and M is the number of users,
     * this vector is filled with either 1 or -1,
     * If a user belongs to the protected group it is +1, otherwise it is -1
     */
    private int[] groupMembershipVector;

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix userFeatureMatrix;

    /**
     * the protected feature e.g. female
     */
    private String protectedAttribute;

    /**
     * feature id mapping
     */
    BiMap<String, Integer> featureIdMapping;

    /**
     * balance
     */
    private double balance;

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
     * users's nearest neighbors for kNN <=0, i.e., all other items
     */
    private Set<Integer> allUsers;

    /**
     * This parameter sets a threshold for similarity, so we only consider the user pairs that their sim > threshold.
     */
    private float minSimThresh;


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
        lambda3 = conf.getFloat("rec.bnslim.regularization.l3", 1.0f);
        minSimThresh = conf.getFloat("rec.bnslim.minsimilarity", -1.0f);
        protectedAttribute = conf.get("data.protected.feature"); // no default value is set here.


        System.out.println("***");
        System.out.println("l1 reg: " + regL1Norm);
        System.out.println("l2 reg: " + regL2Norm);
        System.out.println("balance controller l3: " + lambda3);
        System.out.println("***");

        coefficientMatrix = new DenseMatrix(numUsers, numUsers);
        // initial guesses: make smaller guesses (e.g., W.init(0.01)) to speed up the training
        coefficientMatrix.init();
        similarityMatrix = context.getSimilarity().getSimilarityMatrix();


        for(int userIdx = 0; userIdx < this.numUsers; ++userIdx) {
            this.coefficientMatrix.set(userIdx, userIdx, 0.0d);
        } //iterate through all of the users , initialize

        //create the nn matrix
        createUserNNs();

        // generating the groupMembershipVector from userFeatureMatrix
        userFeatureMatrix = getDataModel().getFeatureAppender().getUserFeatures();
        featureIdMapping = getDataModel().getFeatureAppender().getUserFeatureMap();
        BiMap<Integer, String> userMappingInverse = userMappingData.inverse();


        // the membership is either 1 or -1, 1 for the protected group and -1 for the unprotected
//        int itemMembership = -1; //unprotected
        groupMembershipVector = new int[numUsers];

        for (int userIdx = 0; userIdx < numUsers; userIdx++) {
            int userId = Integer.parseInt(userMappingInverse.get(userIdx)); // convert String to Integer

            int userMembership = -1; //unprotected
            if (userFeatureMatrix.getColumnsSet(userIdx).size() > 0) {
                if (userFeatureMatrix.get(userIdx, featureIdMapping.get(protectedAttribute)) == 1) {
                    userMembership = 1; //protected
                }
            }
            groupMembershipVector[userIdx] = userMembership;
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
            for (int userIdx = 0; userIdx < numUsers; userIdx++) {
                // if we have set knn to a number, it is > 0, otherwise all the users are knns of a user
                Set<Integer> nearestNeighborCollection = knn > 0 ? userNNs[userIdx] : allUsers;

                //all the ratings of userIdx for all the items
                double[] itemRatingEntries = new double[numItems];

                Iterator<VectorEntry> itemItr = trainMatrix.colIterator(userIdx); //should we go through all of the items??????
                while (itemItr.hasNext()) {
                    VectorEntry itemRatingEntry = itemItr.next();
                    itemRatingEntries[itemRatingEntry.index()] = itemRatingEntry.get();
                }

                // for each nearest neighbor nearestNeighborItemIdx, update coefficient Matrix by the coordinate
                // descent update rule
                for (Integer nearestNeighborUserIdx : nearestNeighborCollection) { //user nearest neighbors

                    // get the similarity value
                    double sim = similarityMatrix.get(nearestNeighborUserIdx, userIdx);
                    if (nearestNeighborUserIdx != userIdx && sim > minSimThresh) { // we add this for efficiency purposes.
                        double gradSum = 0.0d, rateSum = 0.0d, errors = 0.0d, userBalanceSumSqr =0.0d, userBalanceSum =0.0d;

                        //ratings of each item for all the other users
                        Iterator<VectorEntry> nnItemRatingItr = trainMatrix.colIterator(nearestNeighborUserIdx);
                        if (!nnItemRatingItr.hasNext()) {
                            continue;
                        }

                        int nnCount = 0;

                        while (nnItemRatingItr.hasNext()) { // now go through the ratings of a user
                            VectorEntry nnItemVectorEntry = nnItemRatingItr.next();
                            int nnItemIdx = nnItemVectorEntry.index();
                            double nnRating = nnItemVectorEntry.get();
                            double rating = itemRatingEntries[nnItemIdx]; //get the rating of the nn user on the main item

//                            double error = rating - predict(nnUserIdx, itemIdx, nearestNeighborItemIdx);
//                            double itemBalance = balancePredictor(nnUserIdx, itemIdx, nearestNeighborItemIdx); // Calculating Sigma(pk . wik)
                            // the below function calcualtes both the predicted rating and the itemBalance at the same time
                            double error = rating - predictBothRatingAndBalance(userIdx, nnItemIdx, nearestNeighborUserIdx);
                            double userBalance = balance; // balance is calculated in the predictBothRatingAndBalance() and the value is updated.


                            userBalanceSumSqr += userBalance * userBalance; //item balance squared
                            userBalanceSum += userBalance;
                            gradSum += nnRating * error; //
                            rateSum += nnRating * nnRating; // sigma r^2

                            errors += error * error;
                            nnCount++;
                        }

                        userBalanceSumSqr /= nnCount;
                        userBalanceSum /= nnCount;

                        gradSum /= nnCount;
                        rateSum /= nnCount;
                        errors /= nnCount;


                        double coefficient = coefficientMatrix.get(nearestNeighborUserIdx, userIdx);
                        Integer userMembership = groupMembershipVector[userIdx];
                        // nnMembership or itemMembership?
                        loss += 0.5 * errors + 0.5 * regL2Norm * coefficient * coefficient +
                                regL1Norm * coefficient + 0.5 * lambda3 * userBalanceSumSqr ;


                        weights += userBalanceSum; // weights

                        /** Implementing Soft Thresholding => S(beta, Lambda1)+
                         * beta = Sigma(r - Sigma(wr)) + lambda3 * p * Sigma(wp)
                         * & Sigma(r - Sigma(wr)) = gradSum
                         * & nnMembership = p
                         * & Sigma(wp) = itemBalanceSum
                         */

                        double beta = gradSum + (lambda3 * userMembership * userBalanceSum) ; //adding item balance to the gradsum
                        double update = 0.0d; //weight

                        if (regL1Norm < Math.abs(beta)) {
                            if (beta > 0) {
                                update = (beta - regL1Norm) / (regL2Norm + rateSum + lambda3);
                            } else {
                                // One doubt: in this case, wij<0, however, the
                                // paper says wij>=0. How to gaurantee that?
                                update = (beta + regL1Norm) / (regL2Norm + rateSum + lambda3);
                            }
                        }

                        coefficientMatrix.set(nearestNeighborUserIdx, userIdx, update); //update the coefficient
                    }
                }
            }

            if (isConverged(iter) && earlyStop) {
                break;
            }
        }
    }

    /**
     *  calculate the balance for each item according to their membership weight and their coefficient
     *  diag(PW) ^ 2
     *  for all of the nnItems of an item
     *
     * In this function we'll try to both calculate the predicted rating for user u and item i, and
     * also balance term for efficiency purposes.\
     *
     * @param userIdx
     * @param itemIdx
     * @param excludedUserIdx
     * @return
     */
    protected double predictBothRatingAndBalance(int userIdx, int itemIdx, int excludedUserIdx) {

        double predictRating = 0;
        balance = 0;

        Iterator<VectorEntry> userEntryIterator = trainMatrix.rowIterator(itemIdx);

        while (userEntryIterator.hasNext()) {
            //iterate through the nearest neighbors of a user and calculate the prediction accordingly
            VectorEntry userEntry = userEntryIterator.next();
            int nearestNeighborUserIdx = userEntry.index(); //nn user index
            double nearestNeighborPredictRating = userEntry.get();

            if (userNNs[userIdx].contains(nearestNeighborUserIdx) && nearestNeighborUserIdx != excludedUserIdx) {

                double coeff = coefficientMatrix.get(nearestNeighborUserIdx, userIdx);
                //predictRating += nearestNeighborPredictRating * coefficientMatrix.get(nearestNeighborUserIdx, userIdx);
                //Calculate the prediction
                predictRating += nearestNeighborPredictRating * coeff;
                //calculate the user balance
                //take p vector, multiply by the coefficients of neighbors (dot product)
                balance += groupMembershipVector[nearestNeighborUserIdx] * coeff;
            }
        }
        return predictRating;
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
        if (!(null != userNNs && userNNs.length > 0)) {
            createUserNNs();
        }
        return predictBothRatingAndBalance(userIdx, itemIdx, -1);
    }


    /**
     * Create user KNN list.
     */
    public void createUserNNs() {
        userNNs = new HashSet[numUsers];

        // find the nearest neighbors for each user based on user similarity
        List<Map.Entry<Integer, Double>> tempUserSimList;
        if (knn > 0) {
            for (int userIdx = 0; userIdx < numUsers; ++userIdx) {
                SparseVector similarityVector = similarityMatrix.row(userIdx);
                if (knn < similarityVector.size()) {
                    tempUserSimList = new ArrayList<>(similarityVector.size() + 1);
                    Iterator<VectorEntry> simItr = similarityVector.iterator();
                    while (simItr.hasNext()) {
                        VectorEntry simVectorEntry = simItr.next();
                        //efficiency : if simVectorEntry.get or the similarity is lower than a threshold, don't include it in the userNNs
                        tempUserSimList.add(new AbstractMap.SimpleImmutableEntry<>(simVectorEntry.index(), simVectorEntry.get()));
                    }
                    tempUserSimList = Lists.sortListTopK(tempUserSimList, true, knn);
                    userNNs[userIdx] = new HashSet<>((int) (tempUserSimList.size() / 0.5)); // why 0.5?? why not * 2?
                    for (Map.Entry<Integer, Double> tempUserSimEntry : tempUserSimList) {
                        userNNs[userIdx].add(tempUserSimEntry.getKey());
                    }
                } else {
                    userNNs[userIdx] = similarityVector.getIndexSet();
                }
            }
        } else {
            allUsers = new HashSet<>(trainMatrix.rows());
        }
    }
}
