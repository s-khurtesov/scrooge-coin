import java.util.ArrayList;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
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
                if (input.prevTxHash == anotherInput.prevTxHash && 
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

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        return null;
    }

}
