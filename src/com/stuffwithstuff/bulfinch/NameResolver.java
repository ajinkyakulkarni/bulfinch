package com.stuffwithstuff.bulfinch;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves all name references in a function. Modifies the AST to include
 * resolution information.
 */
public class NameResolver implements ExprVisitor<Void> {
  public static void resolveTopLevel(FunctionExpr function) {
    new NameResolver(null).resolve(function);
  }
  
  private void resolve(FunctionExpr function) {
    mLocals.addAll(function.getParameters());
    function.getBody().accept(this, null);
    function.resolve(mLocals, mUpvars);
  }

  @Override
  public void visit(AssignExpr expr, Void dummy) {
    resolveName(this, expr.getName());
    expr.getValue().accept(this, dummy);
  }
  
  @Override
  public void visit(CallExpr expr, Void dummy) {
    expr.getFunction().accept(this, dummy);
    for (Expr arg : expr.getArgs()) arg.accept(this, dummy);
  }

  @Override
  public void visit(FunctionExpr expr, Void dummy) {
    // Resolve the function itself.
    new NameResolver(this).resolve(expr);
  }

  @Override
  public void visit(IfExpr expr, Void dummy) {
    expr.getCondition().accept(this, dummy);
    expr.getThenArm().accept(this, dummy);
    expr.getElseArm().accept(this, dummy);
  }
  
  @Override
  public void visit(NameExpr expr, Void dummy) {
    resolveName(this, expr.getName());
  }

  @Override
  public void visit(SequenceExpr sequence, Void dummy) {
    for (Expr expr : sequence.getExpressions()) {
      expr.accept(this, dummy);
    }
  }

  @Override
  public void visit(StringExpr expr, Void dummy) {
    // Do nothing.
  }

  @Override
  public void visit(VarExpr expr, Void dummy) {
    mLocals.add(expr.getName().getIdentifier());
    expr.getName().resolveLocal(mLocals.size() - 1);
    
    expr.getValue().accept(this, dummy);
  }

  private NameResolver(NameResolver outerFunction) {
    mOuterFunction = outerFunction;
    mLocals = new ArrayList<String>();
    mUpvars = new ArrayList<UpvarRef>();
  }

  /**
   * Finds which outer scope a given name is defined in. If found, it will
   * fill chain with all of the scopes from where it was defined to this one
   * (inclusive). If the name isn't found, chain will be left empty.
   */
  private UpvarRef resolveName(NameResolver function, Name name) {
    // Bail if we run out of scopes.
    if (function == null) {
      // If we got here, we couldn't find the name in any scope, so we'll
      // assume it's global.
      name.resolveGlobal();
      
      return null;
    }
    
    // See if the name is defined here.
    int local = function.mLocals.indexOf(name.getIdentifier());
    if (local != -1) {
      if (function == this) {
        // It is defined in the current scope, resolve it as local.
        name.resolveLocal(local);
      }
      
      return new UpvarRef(name.getIdentifier(), true, local);
    }
    
    // Recurse upwards.
    UpvarRef upvar = resolveName(function.mOuterFunction, name);
    
    // Just unwind if we never found the name in any scope.
    if (upvar == null) return null;

    // Add an upvar to this scope. This flattens the closure: if we refer to
    // a variable defined outside of our immediately enclosing function, each
    // intervening function will copy that variable into its upvars so we can
    // walk it down to the function that uses it.
    upvar.setSlot(function.mUpvars.size());
    function.mUpvars.add(upvar);

    if (function == this) {
      // Resolve the name to the final upvar.
      name.resolveUpvar(upvar);
    }
    
    // Return the upvar index. We translate it into a negative number so we
    // can distinguish between locals and upvars.
    return new UpvarRef(name.getIdentifier(), false, upvar.getSlot());
  }
  
  int findUpvar(List<UpvarRef> upvars, Name name) {
    for (int index = 0; index < upvars.size(); index++) {
      if (upvars.get(index).getName().equals(name.getIdentifier())) {
        return index;
      }
    }
    
    // Not found.
    return -1;
  }
  
  private final NameResolver mOuterFunction;
  private final List<String> mLocals;
  private final List<UpvarRef> mUpvars;
}
