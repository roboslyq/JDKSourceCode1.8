package com.roboslyq.datastuct.tree;

public class BinaryTreeNode<T> {

    private T t;
    private BinaryTreeNode<T> leftNode;
    private BinaryTreeNode<T> rightNode;
    public BinaryTreeNode() {
    }
    public BinaryTreeNode(T t) {
        this.t = t;
    }

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }

    public BinaryTreeNode<T> getLeftNode() {
        return leftNode;
    }

    public void setLeftNode(BinaryTreeNode<T> leftNode) {
        this.leftNode = leftNode;
    }

    public BinaryTreeNode<T> getRightNode() {
        return rightNode;
    }

    public void setRightNode(BinaryTreeNode<T> rightNode) {
        this.rightNode = rightNode;
    }
}
