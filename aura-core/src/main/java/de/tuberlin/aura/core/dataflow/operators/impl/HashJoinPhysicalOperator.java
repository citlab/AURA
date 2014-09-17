package de.tuberlin.aura.core.dataflow.operators.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tuberlin.aura.core.common.utils.IVisitor;
import de.tuberlin.aura.core.dataflow.operators.base.AbstractBinaryPhysicalOperator;
import de.tuberlin.aura.core.dataflow.operators.base.IOperatorEnvironment;
import de.tuberlin.aura.core.dataflow.operators.base.IPhysicalOperator;
import de.tuberlin.aura.core.record.TypeInformation;
import de.tuberlin.aura.core.record.tuples.Tuple2;

/**
 *
 */
public final class HashJoinPhysicalOperator<I1,I2> extends AbstractBinaryPhysicalOperator<I1,I2,Tuple2<I1,I2>> {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final TypeInformation input1TypeInfo;

    private final TypeInformation input2TypeInfo;

    private final Map<List<Object>,I1> buildSide;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    /**
     * Constructor.
     * @param inputOp1
     * @param inputOp2
     */
    public HashJoinPhysicalOperator(final IOperatorEnvironment environment,
                                    final IPhysicalOperator<I1> inputOp1,
                                    final IPhysicalOperator<I2> inputOp2) {

        super(environment, inputOp1, inputOp2);

        this.input1TypeInfo = getEnvironment().getProperties().input1Type;

        this.input2TypeInfo = getEnvironment().getProperties().input2Type;

        this.buildSide = new HashMap<>();

        // sanity check.
        if (getEnvironment().getProperties().joinKeyIndices1.length != getEnvironment().getProperties().joinKeyIndices1.length)
            throw new IllegalStateException("joinKeyIndices1.length != joinKeyIndices2.length");
        // TODO: check types!
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public void open() throws Throwable {
        super.open();

        I1 in1 = null;

        // Construct build-side. Consume from <code>input1<code> all
        // tuples and store them in a simple HashMap.
        inputOp1.open();

        in1 = inputOp1.next();

        while (in1 != null) {
            final List<Object> key1 = new ArrayList<>(getEnvironment().getProperties().joinKeyIndices1.length);

            for (final int[] selectorChain : getEnvironment().getProperties().joinKeyIndices1) {
                key1.add(input1TypeInfo.selectField(selectorChain, in1));
            }

            buildSide.put(key1, in1);

            in1 = inputOp1.next();
        }

        inputOp1.close();

        inputOp2.open();
    }

    @Override
    public Tuple2<I1,I2> next() throws Throwable {

        I1 in1 = null;
        I2 in2 = null;

        while (in1 == null) {
            in2 = inputOp2.next();
            if (in2 != null) {
                final List<Object> key2 = new ArrayList<>(getEnvironment().getProperties().joinKeyIndices2.length);

                for (final int[] selectorChain : getEnvironment().getProperties().joinKeyIndices2) {
                    key2.add(input1TypeInfo.selectField(selectorChain, in2));
                }

                in1 = buildSide.get(key2);
            } else {
                return null;
            }
        }

        return new Tuple2<>(in1, in2);
    }

    @Override
    public void close() throws Throwable {
        super.close();
        inputOp2.close();
    }

    /**
     *
     * @param visitor
     */
    @Override
    public void accept(final IVisitor<IPhysicalOperator> visitor) {
        visitor.visit(this);
    }
}
