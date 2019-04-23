/*
 * Copyright 1997-2019 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.matrix.decomposition;

import static org.ojalgo.function.constant.PrimitiveMath.*;

import org.ojalgo.RecoverableCondition;
import org.ojalgo.array.Raw2D;
import org.ojalgo.array.blas.DOT;
import org.ojalgo.function.aggregator.Aggregator;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.RawStore;
import org.ojalgo.scalar.PrimitiveScalar;
import org.ojalgo.structure.Access2D;
import org.ojalgo.structure.Access2D.Collectable;
import org.ojalgo.structure.Structure2D;

final class RawLU extends RawDecomposition implements LU<Double> {

    private Pivot myPivot;

    /**
     * Not recommended to use this constructor directly. Consider using the static factory method
     * {@linkplain org.ojalgo.matrix.decomposition.LU#make(Access2D)} instead.
     */
    RawLU() {
        super();
    }

    public Double calculateDeterminant(final Access2D<?> matrix) {

        final double[][] data = this.reset(matrix, false);

        this.getInternalStore().fillMatching(matrix);

        this.doDecompose(data, true);

        return this.getDeterminant();
    }

    public boolean decompose(final Access2D.Collectable<Double, ? super PhysicalStore<Double>> matrix) {

        final double[][] data = this.reset(matrix, false);

        matrix.supplyTo(this.getInternalStore());

        return this.doDecompose(data, true);
    }

    public boolean decomposeWithoutPivoting(Collectable<Double, ? super PhysicalStore<Double>> matrix) {

        final double[][] data = this.reset(matrix, false);

        matrix.supplyTo(this.getInternalStore());

        return this.doDecompose(data, false);
    }

    public Double getDeterminant() {
        final int m = this.getRowDim();
        final int n = this.getColDim();
        if (m != n) {
            throw new IllegalArgumentException("RawStore must be square.");
        }
        final double[][] LU = this.getInternalData();
        double d = myPivot.signum();
        for (int j = 0; j < n; j++) {
            d *= LU[j][j];
        }
        return d;
    }

    public MatrixStore<Double> getInverse() {
        final int tmpRowDim = this.getRowDim();
        return this.doGetInverse(this.allocate(tmpRowDim, tmpRowDim));
    }

    public MatrixStore<Double> getInverse(final PhysicalStore<Double> preallocated) {
        return this.doGetInverse(preallocated);
    }

    public MatrixStore<Double> getL() {
        return this.getInternalStore().logical().triangular(false, true).get();
    }

    public int[] getPivotOrder() {
        return myPivot.getOrder();
    }

    public int getRank() {

        int retVal = 0;

        final RawStore internalStore = this.getInternalStore();

        double largestValue = internalStore.aggregateDiagonal(Aggregator.LARGEST);

        for (int ij = 0, limit = this.getMinDim(); ij < limit; ij++) {
            if (!internalStore.isSmall(ij, ij, largestValue)) {
                retVal++;
            }
        }

        return retVal;
    }

    public MatrixStore<Double> getSolution(final Collectable<Double, ? super PhysicalStore<Double>> rhs) {
        final DecompositionStore<Double> tmpPreallocated = this.allocate(rhs.countRows(), rhs.countColumns());
        return this.getSolution(rhs, tmpPreallocated);
    }

    @Override
    public MatrixStore<Double> getSolution(final Collectable<Double, ? super PhysicalStore<Double>> rhs, final PhysicalStore<Double> preallocated) {

        this.collect(rhs).logical().row(myPivot.getOrder()).supplyTo(preallocated);

        return this.doSolve(preallocated);
    }

    public MatrixStore<Double> getU() {
        return this.getInternalStore().logical().triangular(true, false).get();
    }

    @Override
    public MatrixStore<Double> invert(final Access2D<?> original, final PhysicalStore<Double> preallocated) throws RecoverableCondition {

        final double[][] tmpData = this.reset(original, false);

        this.getInternalStore().fillMatching(original);

        this.doDecompose(tmpData, true);

        if (this.isSolvable()) {
            return this.getInverse(preallocated);
        } else {
            throw RecoverableCondition.newMatrixNotInvertible();
        }
    }

    /**
     * Is the matrix nonsingular?
     *
     * @return true if U, and hence A, is nonsingular.
     */
    public boolean isFullRank() {

        final RawStore raw = this.getInternalStore();

        double largestValue = Math.sqrt(raw.aggregateDiagonal(Aggregator.LARGEST));

        for (int ij = 0, limit = this.getMinDim(); ij < limit; ij++) {
            if (PrimitiveScalar.isSmall(largestValue, raw.doubleValue(ij, ij))) {
                return false;
            }
        }

        return true;
    }

    public boolean isPivoted() {
        return myPivot.isModified();
    }

    public PhysicalStore<Double> preallocate(final Structure2D template) {
        return this.allocate(template.countRows(), template.countRows());
    }

    public PhysicalStore<Double> preallocate(final Structure2D templateBody, final Structure2D templateRHS) {
        return this.allocate(templateBody.countRows(), templateRHS.countColumns());
    }

    @Override
    public void reset() {

        super.reset();

        myPivot = null;
    }

    @Override
    public MatrixStore<Double> solve(final Access2D<?> body, final Access2D<?> rhs, final PhysicalStore<Double> preallocated) throws RecoverableCondition {

        final double[][] tmpData = this.reset(body, false);

        this.getInternalStore().fillMatching(body);

        this.doDecompose(tmpData, true);

        if (this.isSolvable()) {

            MatrixStore.PRIMITIVE.makeWrapper(rhs).row(myPivot.getOrder()).supplyTo(preallocated);

            return this.doSolve(preallocated);

        } else {
            throw RecoverableCondition.newEquationSystemNotSolvable();
        }
    }

    /**
     * Use a "left-looking", dot-product, Crout/Doolittle algorithm, essentially copied from JAMA.
     */
    private boolean doDecompose(final double[][] data, boolean pivoting) {

        final int numbRows = this.getRowDim();
        final int numbCols = this.getColDim();

        myPivot = new Pivot(numbRows);

        final double[] colJ = new double[numbRows];

        // Outer loop.
        for (int j = 0; j < numbCols; j++) {

            // Make a copy of the j-th column to localize references.
            for (int i = 0; i < numbRows; i++) {
                colJ[i] = data[i][j];
            }

            // Apply previous transformations.
            for (int i = 0; i < numbRows; i++) {
                // Most of the time is spent in the following dot product.
                data[i][j] = colJ[i] -= DOT.invoke(data[i], 0, colJ, 0, 0, Math.min(i, j));
            }

            if (pivoting) {
                // Find pivot and exchange if necessary.
                int p = j;
                double valP = ABS.invoke(colJ[p]);
                for (int i = j + 1; i < numbRows; i++) {
                    if (ABS.invoke(colJ[i]) > valP) {
                        p = i;
                        valP = ABS.invoke(colJ[i]);
                    }
                }
                if (p != j) {
                    Raw2D.exchangeRows(data, j, p);
                    myPivot.change(j, p);
                }
            }

            // Compute multipliers.
            if (j < numbRows) {
                final double tmpVal = data[j][j];
                if (tmpVal != ZERO) {
                    for (int i = j + 1; i < numbRows; i++) {
                        data[i][j] /= tmpVal;
                    }
                }
            }

        }

        return this.computed(true);
    }

    private MatrixStore<Double> doGetInverse(final PhysicalStore<Double> preallocated) {

        final int[] tmpPivotOrder = myPivot.getOrder();
        final int tmpRowDim = this.getRowDim();
        for (int i = 0; i < tmpRowDim; i++) {
            preallocated.set(i, tmpPivotOrder[i], ONE);
        }

        final RawStore tmpBody = this.getInternalStore();

        preallocated.substituteForwards(tmpBody, true, false, !myPivot.isModified());

        preallocated.substituteBackwards(tmpBody, false, false, false);

        return preallocated;
    }

    private MatrixStore<Double> doSolve(final PhysicalStore<Double> preallocated) {

        final MatrixStore<Double> tmpBody = this.getInternalStore();

        preallocated.substituteForwards(tmpBody, true, false, false);

        preallocated.substituteBackwards(tmpBody, false, false, false);

        return preallocated;
    }

    @Override
    protected boolean checkSolvability() {
        return (this.getRowDim() == this.getColDim()) && this.isFullRank();
    }

}
