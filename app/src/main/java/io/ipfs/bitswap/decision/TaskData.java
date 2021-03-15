package io.ipfs.bitswap.decision;

public class TaskData {

    // Tasks can be want-have or want-block
    boolean IsWantBlock;
    // Whether to immediately send a response if the block is not found
    boolean SendDontHave;
    // The size of the block corresponding to the task
    int BlockSize;
    // Whether the block was found
    boolean HaveBlock;

    public TaskData(int blockSize, boolean haveBlock, boolean isWantBlock, boolean sendDontHave) {
        this.BlockSize = blockSize;
        this.SendDontHave = sendDontHave;
        this.IsWantBlock = isWantBlock;
        this.HaveBlock = haveBlock;
    }


}
