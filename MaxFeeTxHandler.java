import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

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
    
            return (inputSum - outputSum) / tx.getRawTx().length;
        }

    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();

        TransactionFeesComparator feeComparator = new TransactionFeesComparator();
        Arrays.sort(possibleTxs, feeComparator.reversed());

        for (Transaction tx : possibleTxs) {
            if (! isValidTx(tx)) {
                continue;
            }

            acceptedTxs.add(tx);

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
        }
        
        return acceptedTxs.toArray(new Transaction[0]);
    }

}
