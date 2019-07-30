package net.librec.eval.rating;

import static org.junit.Assert.assertEquals;

import net.librec.eval.ranking.StatisticalParityEvaluator;
import org.junit.Before;
import org.junit.Test;


public class StatisticalParityEvaluatorTestCase {

    @Before

    @Test
    public void evaluatesExpression() {

        StatisticalParityEvaluator statParEval = new StatisticalParityEvaluator();
        double score = statParEval.evaluate(sparseMatrix, recommendedList);
        assertEquals(3.0, 4.0, 1.1);
    }


}
