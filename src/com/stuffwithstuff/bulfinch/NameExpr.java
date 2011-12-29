package com.stuffwithstuff.bulfinch;

public class NameExpr implements Expr {
  public NameExpr(final String name) {
    mName = name;
  }

  public String getName() {
    return mName;
  }

  @Override
  public String toString() {
    return mName;
  }

  public <T> void accept(ExprVisitor<T> visitor, T arg) {
    visitor.visit(this, arg);
  }

  private final String mName;
}