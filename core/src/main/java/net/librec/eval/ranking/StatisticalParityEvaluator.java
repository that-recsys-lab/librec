package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.data.convertor.appender.ItemFeatureAppender;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class StatisticalParityEvaluator extends AbstractRecommenderEvaluator {

    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix
     *            the given test set
     * @param recommendedList
     *            the list of recommended items
     * @return evaluate result
     *         (number of protected items / protected group size) /
     *         (number of unprotected items / unprotected group size )
     */

    protected SparseMatrix itemFeatureMatrix;

    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) throws IOException {
        // construct protected and unprotected item set
        // String inputFilePath = conf.get("dfs.membership.dir") + "/" + conf.get("data.input.path") + "/" + "/membership.csv";
        String inputFilePath = conf.get("dfs.data.dir") + "/" + conf.get("dfs.membership.dir");
        System.out.print(inputFilePath);
        // MembershipUtil membershipUtil = new MembershipUtil(inputFilePath, true);
        itemFeatureMatrix = ((ItemFeatureAppender) getDataModel().getDataAppender()).getUserAppender();


        double totalProtected = 0.0;
        double totalUnprotected = 0.0;
        int numUsers = testMatrix.numRows();
        BiMap<String, Integer> itemMapping = getDataModel().getItemMappingData();

        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {
		        int unprotectedNum = 0;
                int protectedNum = 0;
                List<ItemEntry<Integer, Double>> recommendListByUser = recommendedList.getItemIdxListByUserIdx(userID);

                // calculate rate
                int topK = this.topN <= recommendListByUser.size() ? this.topN : recommendListByUser.size();
                for (int indexOfItem = 0; indexOfItem < topK; indexOfItem++) {
                    // totalRecommendation++;
                    int itemID = recommendListByUser.get(indexOfItem).getKey();
                    if (!membershipUtil.protectedSet.contains(itemMapping.inverse().get(itemID))){
                        unprotectedNum++;
                    } else {
                        protectedNum++;
                    }
                }

                totalProtected += protectedNum;
                totalUnprotected += unprotectedNum;
            }
        }

        // (number of protected items / protected group size) /
        // (number of unprotected items / unprotected group size )
        double protectedRatio =  (totalProtected / membershipUtil.protectedSet.size());
        double unprotectedRatio = (totalUnprotected / membershipUtil.unprotectedSet.size());
        double relativeChance = protectedRatio / unprotectedRatio;
        System.out.println(relativeChance);
        return relativeChance;

    }
}
