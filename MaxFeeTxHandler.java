import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

public class MaxFeeTxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        double inputSum = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            
            // (1) all outputs claimed by {@code tx} are in the current UTXO pool
            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);
            if (prevOutput == null) {
                return false;
            }

            inputSum += prevOutput.value;

            // (2) the signatures on each input of {@code tx} are valid
            byte[] message = tx.getRawDataToSign(i);
            byte[] signature = input.signature;
            if (! Crypto.verifySignature(prevOutput.address, message, signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by {@code tx}
            for (int j = i + 1; j < inputs.size(); j++) {
                Transaction.Input anotherInput = inputs.get(j);
                if (Arrays.equals(input.prevTxHash, anotherInput.prevTxHash) && 
                    input.outputIndex == anotherInput.outputIndex) {
                    return false;
                }
            }
        }

        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        double outputSum = 0;
        for (Transaction.Output output : outputs) {
            double outputValue = output.value;

            // (4) all of {@code tx}s output values are non-negative
            if (outputValue < 0) {
                return false;
            }

            outputSum += outputValue;
        }

        // (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
        //     values; and false otherwise.
        if (inputSum >= outputSum) {
            return true;
        }
        else {
            return false;
        }
    }

    public class TransactionFeesComparator implements Comparator<Transaction> {

        @Override
        public int compare(Transaction tx1, Transaction tx2) {
           return Double.compare(getFee(tx1), getFee(tx2));
        }
        
    }

    private double getFee(Transaction tx) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        double inputSum = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);

            if (prevOutput != null) {
                inputSum += prevOutput.value;
            }
        }

        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        double outputSum = 0;
        for (Transaction.Output output : outputs) {
            double outputValue = output.value;
            outputSum += outputValue;
        }

        // return inputSum - outputSum;
        return (inputSum - outputSum) / tx.getRawTx().length;
    }

    private ArrayList<Transaction>[][] generateSDT(Transaction[] possibleTxs, int k1, int k2) {
        ArrayList<Transaction>[][] T = new ArrayList[k1][k2];

        for (int i = 0; i < k1; i++) {
            T[i] = new ArrayList[k2];
            for (int j = 0; j < k2; j++) {
                T[i][j] = new ArrayList<Transaction>();
            }
        }
        
        HashMap<Transaction, double[]> map = new HashMap<Transaction, double[]>();

        int maxSize = 0, minSize = 0;
        double maxDen = 0, minDen = 0;

        for (Transaction tx : possibleTxs) {
            int txSize = tx.getRawTx().length;
            // double txFee = getFee(tx);
            // double txDen = txFee / txSize;
            double txDen = getFee(tx);

            maxSize = Integer.max(maxSize, txSize);
            maxDen = Double.max(maxDen, txDen);
            minSize = Integer.min(minSize, txSize);
            minDen = Double.min(minDen, txDen);

            double[] val = { txSize, txDen };
            map.put(tx, val);
        }

        final int fmaxSize = maxSize, fminSize = minSize;
        final double fmaxDen = maxDen, fminDen = minDen;

        map.forEach((k, v) -> { 
            double txSize = v[0] = (v[0] - fminSize) / (fmaxSize - fminSize);
            double txDen = v[1] = (v[1] - fminDen) / (fmaxDen - fminDen);

            int s = (int) Math.ceil(txSize * (k1 - 1));
            int d = (int) Math.ceil(txDen * (k2 - 1));

            T[s][d].add(k);
        });

        // TODO: Use map for sorting (precalculated stats)
        TransactionFeesComparator comparator = new TransactionFeesComparator();
        for (int i = 0; i < k1; i++) {
            for (int j = 0; j < k2; j++) {
                T[i][j].sort(comparator.reversed());
            }
        }

        return T;
    }

    private Transaction updatePool(Transaction tx) {
        // Finalise
        if (! isValidTx(tx)) {
            return null;
        }

        // Remove utxo associated with tx's inputs
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            
            // Transaction is correct, so this utxo is definitely in the pool
            utxoPool.removeUTXO(utxo);
        }

        // Add utxo associated with tx's outputs
        byte[] txHash = tx.getHash();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            Transaction.Output output = outputs.get(i);
            UTXO utxo = new UTXO(txHash, i);

            utxoPool.addUTXO(utxo, output);
        }

        return tx;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Vector<Transaction> validTxs = new Vector<Transaction>();
        ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();

        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                validTxs.add(tx);
            }
        }

        int k1 = 10, k2 = 10;
        ArrayList<Transaction>[][] T = generateSDT(validTxs.toArray(new Transaction[0]), k1, k2);

        int j = k2 - 1;
        boolean terminate = false;
        while (terminate == false && j >= 0) {
            // Select from SDT
            Transaction tx, res;
            boolean selected = false;
            int si = k1 - 1;
            int i = k1 - 1;

            // first, try to find any transaction with density class j among the size-classes that are guaranteed to fit.
            while (i > 0 && selected == false) {
                res = null;
                while (res == null && ! T[i][j].isEmpty()) {
                    tx = T[i][j].remove(0);
                    res = updatePool(tx);
                }
                if (res != null) {
                    acceptedTxs.add(res);
                    selected = true;
                }
                else {
                    i--;
                }
            }
            
            // if failed to find a suitable transaction, try all transactions at size-class si (they might fit or not)
            int t = 0;
            while (t < T[si][j].size() && selected == false) {
                res = null;
                while (res == null && ! T[si][j].isEmpty()) {
                    tx = T[si][j].remove(t);
                    res = updatePool(tx);
                }
                if (res != null) {
                    acceptedTxs.add(res);
                    selected = true;
                }
                else {
                    t++;
                }
            }

            // if failed to find a suitable transaction, then there was no good transaction in the jth density class; decrement j
            if (selected == false) {
                j--;
                if(j < 0) {
                    terminate = true; // no transaction in the entire table fits
                }
            }
        }
        
        return acceptedTxs.toArray(new Transaction[0]);
    }

}
