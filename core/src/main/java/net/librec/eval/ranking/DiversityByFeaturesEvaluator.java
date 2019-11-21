package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.data.DataModel;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.math.structure.SymmMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import net.librec.similarity.JaccardSimilarity;
import org.apache.commons.lang.StringUtils;
import net.librec.math.structure.SparseVector;

import java.util.List;

/**
 *
 *  *
 * @author Nasim Sonboli, Florencia Cabral
 */

public class DiversityByFeaturesEvaluator extends AbstractRecommenderEvaluator {

    /**
     *  featureSimilarityMatrix, a similarityMatrix based on item features.
     */
    protected SymmMatrix featureSimilarityMatrix;

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix itemFeatureMatrix;

    public void setDataModel(DataModel datamodel) { // USE
        super.setDataModel(datamodel);
    }

    /**
     * rebuild a similarity matrix which uses features instead of ratings.
     * It uses Jaccard as the similarity metric but it can be changed.
     *
     * @return the item -item similarity matrix based on item features
     */
    public SymmMatrix buildFeatureSimilarityMatrix()
    {
        // loading item features
         SparseMatrix itemFeatureMatrix = getDataModel().getFeatureAppender().getItemFeatures();

        // int numFeatures = itemFeatureMatrix.numColumns();
        int numElements = itemFeatureMatrix.numRows();
        int count = numElements;

        featureSimilarityMatrix = new SymmMatrix(count);

        for (int i = 0; i < count; i++) {
            SparseVector thisVector = itemFeatureMatrix.row(i);
            if (thisVector.getCount() == 0) {
                continue;
            }

            for (int j = i +  1; j < count; j++) {
                SparseVector thatVector = itemFeatureMatrix.row(j);
                if (thatVector.getCount() == 0) {
                    continue;
                }

                JaccardSimilarity myJaccardSimilarity = new JaccardSimilarity();
                // Q: Are these vectors correct? needs to be checked with Robin. why are they all the same?!
                double sim = myJaccardSimilarity.getCorrelation(thisVector, thatVector);

                if (!Double.isNaN(sim) && sim != 0) {
                    featureSimilarityMatrix.set(i, j, sim);
                }
            }
        }

        return featureSimilarityMatrix;
    }

    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix      the given test set
     * @param recommendedList the list of recommended items
     * @return evaluate result
     */

    // abbreviation doesn't work whatsoever!
    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) {

        double totalDiversity = 0.0;
        int numUsers = testMatrix.numRows();
        int nonZeroNumUsers = 0;

        // matrix of items (rows) and features (columns)
        // if the item has said feature, the entry is a 1, OW is a 0

        if (similarities.containsKey("item")) {

            // SymmMatrix itemSimilarity = similarities.get("item").getSimilarityMatrix(); //we don't need it.

            featureSimilarityMatrix = buildFeatureSimilarityMatrix();
            // build the item item similarity matrix based on item features


            for (int userID = 0; userID < numUsers; userID++) {
                List<ItemEntry<Integer, Double>> recommendArrayListByUser = recommendedList.getItemIdxListByUserIdx(userID);
                if (recommendArrayListByUser.size() > 1) {
                    // calculate the summation of dissimilarities for each pair of items
                    double totalDisSimilarityPerUser = 0.0;
                    int topK = this.topN <= recommendArrayListByUser.size() ? this.topN : recommendArrayListByUser.size();
                    for (int i = 0; i < topK; i++) {
                        for (int j = 0; j < topK; j++) {
                            if (i == j) {
                                continue;
                            }
                            int item1 = recommendArrayListByUser.get(i).getKey();
                            int item2 = recommendArrayListByUser.get(j).getKey();

                            // changed formula to (1-x)/2
                            totalDisSimilarityPerUser += (1.0 - featureSimilarityMatrix.get(item1, item2)) / 2;
                        }
                    }
                    totalDiversity += totalDisSimilarityPerUser * 2 / (topK * (topK - 1));
                    nonZeroNumUsers++;
                }
            }
        }
        return nonZeroNumUsers > 0 ? totalDiversity / nonZeroNumUsers : 0.0d;
    }
}

