import java.util.ArrayList;
import java.util.List;

public class TxHandler {

    protected UTXOPool pool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        pool = new UTXOPool(utxoPool);
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
        double sumInputs = 0;
        double sumOutputs = 0;
        List<UTXO> seenUtxo = new ArrayList<>();
        for(Transaction.Input input: tx.getInputs()){
            if(!inPool(input))
                return false;
            if(!signatureValid(input, tx))
                return false;
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            sumInputs += pool.getTxOutput(utxo).value;
            if(seenUtxo.contains(utxo))
                return false;
            seenUtxo.add(utxo);
        }
        for(Transaction.Output output: tx.getOutputs()){
            if(output.value < 0)
                return false;
            sumOutputs += output.value;
        }
        return !(sumOutputs > sumInputs);
    }

    private boolean inPool(Transaction.Input input) {
        UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
        return pool.contains(utxo);
    }

    private boolean signatureValid(Transaction.Input input, Transaction transaction) {
        int index = transaction.getInputs().indexOf(input);
        Transaction.Output output = pool.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex));
        if(output == null)
            return false;
        return Crypto.verifySignature(output.address, transaction.getRawDataToSign(index), input.signature);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTransactions = new ArrayList<>();
        for(Transaction transaction: possibleTxs){
            if(isValidTx(transaction)) {
                validTransactions.add(transaction);
                updatePool(transaction);
            }
        }
        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

    private void updatePool(Transaction transaction){
        for(Transaction.Input input: transaction.getInputs()){
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            pool.removeUTXO(utxo);
        }
        int outputIndex = 0;
        for(Transaction.Output output: transaction.getOutputs()){
            UTXO utxo = new UTXO(transaction.getHash(), outputIndex++);
            pool.addUTXO(utxo, output);
        }
    }

}
