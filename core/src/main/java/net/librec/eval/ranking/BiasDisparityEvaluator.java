package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.RecommendedList;
import net.librec.recommender.item.ItemEntry;
import net.librec.util.MembershipCategoriesUtil;

import java.io.IOException;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Set;
import java.util.Hashtable;

public class BiasDisparityEvaluator extends AbstractRecommenderEvaluator {

    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix
     *            the given test set
     * @param recommendedList
     *            the list of recommended items
     * @return evaluate result
     */
    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) throws IOException {


        // TODO: create a data set that has categories in the following way
            // itemid, category, binary

            // userid, itemid, rating

        //  TODO: input preference ratio
            // TODO: number of items rated by a specific group within a specific category
        int numUsers = testMatrix.numRows();
        int itemCountInGroup = 0;
        int itemCountInGroupCategory = 0;
        Hashtable selectedCat = new Hashtable();
        Hashtable unselectedCat = new Hashtable();
        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {
            }
        }
            // TODO: number of items rated by the same group as above

        //  TODO: bias for input PR

        //  TODO: output preference ratio

        //  TODO: bias for output PR

        //  TODO: bias disparity

        // construct protected and unprotected item set
        // String inputFilePath = conf.get("dfs.membership.dir") + "/" + conf.get("data.input.path") + "/" + "/membership.csv";
        String inputFilePath = conf.get("dfs.data.dir") + "/" + conf.get("dfs.membership.dir");
        System.out.print(inputFilePath);
        MembershipCategoriesUtil membershipUtil = new MembershipCategoriesUtil(inputFilePath, true);

        double protectedCat = 0.0;
        double totalCat = 0.0;

        double totalProtected = 0.0;
        double totalUnprotected = 0.0;
        int numUsers = testMatrix.numRows();
        int[] numDroppedItemsArray = getConf().getInts("rec.eval.relative.dropped.num");
        BiMap<String, Integer> itemMapping = getDataModel().getItemMappingData();

        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {
                int unprotectedNum = 0;
                int protectedNum = 0;
                int cat = 0;
                // int allCat = membershipUtil.selectedCat

                // int numItemsCat = membershipCatUtil.selectedCat.size();

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
                //userRate = protectedNum / topK;

                totalProtected += protectedNum;
                totalUnprotected += unprotectedNum;
            }
        }

        // double protectedRatio =  (totalProtected / membershipUtil.protectedSet.size());
        // double unprotectedRatio = (totalUnprotected / membershipUtil.unprotectedSet.size());
        // double relativeChance = protectedRatio / unprotectedRatio;
        // System.out.println(relativeChance);
        // return relativeChance;

    }
}
