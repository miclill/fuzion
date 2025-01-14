/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Call
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Call is an expresion that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Call extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty actuals lists to distinguish a call without
   * parenthesis ("a.b") from a call with parenthesises and an empty actual
   * arguments list ("a.b()").
   */
  public static final List<Expr> NO_PARENTHESES = new List<>();


  /**
   * Special value for an empty generics list to distinguish a call without
   * generics ("a.b(x,y)") from a call with an empty actual generics list
   * ("a.b<>(x,y)").
   */
  public static final List<Type> NO_GENERICS = new List<>();


  /**
   * Empty map for general use.
   */
  public static final Map EMPTY_MAP = Map.of();


  /*------------------------  static variables  -------------------------*/

  /**
   * quick-and-dirty way to get unique values for temp fields in
   * findChainedBooleans.
   */
  static int _chainedBoolTempId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * name of called feature, set by parser
   */
  public String name;


  /**
   * For a call a.b.4 with a select clause ".4" to pick a variant from a field
   * of an open generic type, this is the chosen variant.
   */
  int _select;


  /**
   * actual generic arguments, set by parser
   */
  public /*final*/ List<Type> generics; // NYI: Make this final again when resolveTypes can replace a call


  /**
   * Actual arguments, set by parser
   */
  public List<Expr> _actuals;


  /**
   * the target of the call, null for "this". Set by parser
   */
  public Expr target;


  /**
   * The feature that is called by this call, resolved when
   * loadCalledFeature() is called.
   */
  public Feature calledFeature_;


  /**
   * Static type of this call. Set during resolveTypes().
   */
  Type _type;


  /**
   * forFun specifies iff this call is within a function declaration, e.g., the
   * call to "b" in "x(fun a.b)". In this case, the actual arguments list must
   * be empty, independent of the formal arguments expected by b.
   */
  boolean forFun = false;


  /**
   * For static type analysis: This gives the resolved formal argument types for
   * the arguemnts of this call.  During type checking, it has to be checked
   * that the actual arguments can be assigned to these types.
   *
   * The number of resolved formal arguments might be different to the number of
   * formal arguments in case the last formal argument is of an open generic
   * type.
   */
  Type[] resolvedFormalArgumentTypes = null;


  /**
   * Will be set to true for a call to a direct parent feature in an inheritance
   * call.
   */
  public boolean isInheritanceCall_ = false;


  // NYI: Move sid_ to target?
  public int sid_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!

  // For a call to parent in an inherits clause, these are the ids of the
  // argument fields for the parent feature.
  //
  // NYI: remove, used in FUIR.  This should be replaced by explicit assignments to fields
  public int parentCallArgFieldIds_ = -1;


  /**
   * Is this a tail recursive call?
   *
   * This is used to allow cyclic type inferencing of the form
   *
   *   f is
   *     if c
   *       x
   *     else
   *       f
   *
   * Which must return a value of x's type.
   *
   * NYI: This is currently set explicitly by Loop.java. It should be a function
   * that automatically detects tail recursive calls, i.e., calls without
   * dynamic binding with target == current and with no code apart from setting
   * the result to the returned value after the call.
   */
  public boolean _isTailRecursive = false;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor to read a local field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   */
  public Call(SourcePosition pos, String n)
  {
    this(pos, null,n,null, NO_PARENTHESES);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   *
   * @param a
   */
  public Call(SourcePosition pos, String n, List<Expr> a)
  {
    this(pos, null,n,null, a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   *
   * @param g
   *
   * @param a
   */
  public Call(SourcePosition pos, String n, List<Type> g, List<Expr> a)
  {
    this(pos, null,n,g,a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param g
   *
   * @param a
   */
  public Call(SourcePosition pos,
              Expr t, String n, List<Type> g, List<Expr> a)
  {
    super(pos);
    this.target = t;
    this.name = n;
    this._select = -1;
    this.generics = (g == null) ? NO_GENERICS : g;
    this._actuals = a;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param a
   */
  public Call(SourcePosition pos,
              Expr t, String n, List<Expr> a)
  {
    this(pos, t, n, null, a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   */
  public Call(SourcePosition pos, Expr t, String n)
  {
    this(pos, t, n, -1);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos,
              Expr t, String n, String select)
  {
    super(pos);
    this.target = t;
    this.name = n;
    int s = -1;
    try
      {
        s = Integer.parseInt(select);
        check
          (s >= 0); // parser should not allow negative value
      }
    catch (NumberFormatException e)
      {
        Errors.error(pos,
                     "Illegal select clause",
                     "Failed to parse integer '"  +select + "': " + e);
      }
    this._select = s;
    this.generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos, Expr t, String n, int select)
  {
    super(pos);
    this.target = t;
    this.name = n;
    this._select = select;
    this.generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos, Expr t, Feature calledFeature, int select)
  {
    super(pos);
    this.name = calledFeature._featureName.baseName();
    this._select = select;
    this.generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
    this.target = t;
    this.calledFeature_ = calledFeature;
    this._type = null;
  }


  /**
   * A call to an anonymous feature declared in an expression.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param anonymous the anonymous feature
   */
  public Call(SourcePosition pos,
              Feature anonymous)
  {
    this(pos, new This(pos), anonymous);
  }


  /**
   * A call to an anonymous feature declared using "fun a.b.c".
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param target the target, "a.b".
   *
   * @param anonymous the anonymous feature, which is the wrapper created around
   * the call to "c".
   */
  public Call(SourcePosition pos,
              Expr    target,
              Feature anonymous)
  {
    super(pos);
    this.target         = target;
    this.name           = null;
    this._select        = -1;
    this.generics       = NO_GENERICS;
    this._actuals       = Expr.NO_EXPRS;
    this.calledFeature_ = anonymous;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param name
   *
   * @param generics
   *
   * @param actuals
   *
   * @param target
   *
   * @param calledFeature
   *
   * @param type
   */
  public Call(SourcePosition pos,
              String name, List<Type> generics, List<Expr> actuals, Expr target, Feature calledFeature, Type type)
  {
    super(pos);
    this.name = name;
    this._select = -1;
    this.generics = generics;
    this._actuals = actuals;
    this.target = target;
    this.calledFeature_ = calledFeature;
    this._type = type;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * True iff this call was performed giving 0 or more actual arguments in
   * parentheses.  This allows a disinction between "a.b" and "a.b()" if b has
   * no formal arguments and is of a fun type. In this case, "a.b" calls only b,
   * while "a.b()" is syntactic sugar for "a.b.call".
   *
   * @return true if parentheses were present.
   */
  boolean hasParentheses()
  {
    return _actuals != NO_PARENTHESES;
  }


  /**
   * Find all the inner declarations within this call
   *
   * @param outer the root feature that contains this statement.  For
   * all found feature declarations, the outer feature will be set to
   * this value.
   */
  public void findDeclarations(Feature outer)
  {
    if (name == null)
      { /* this is an anonymous feature declaration */
        check
          (Errors.count() > 0  || calledFeature_ != null);

        if (calledFeature_ != null)
          {
            calledFeature_.findDeclarations(outer);
          }
      }
  }


  /**
   * Get the type of the target.  In case the target's type is a generic type
   * parameter, return its constraint.
   *
   * @return the type of the target.
   */
  private Type targetTypeOrConstraint()
  {
    if (PRECONDITIONS) require
                         (target != null);

    var result = target.type();
    if (result.isGenericArgument())
      {
        var g = result.genericArgument();
        result = g.constraint().resolve(g.feature());
      }

    if (POSTCONDITIONS) ensure
      (!result.isGenericArgument());
    return result;
  }


  /**
   * Find all the candidates of features that might be called at this point as
   * long as the argument count is ignored.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * @return the map of FeatureName to Features of the found candidates. May be
   * empty. ERROR_MAP in case an error occured and was reported already.
   */
  private Feature targetFeature(Resolution res, Feature thiz)
  {
    // are we searching for features called via thiz' inheritance calls?
    boolean inh = thiz.state() == Feature.State.RESOLVING_INHERITANCE;
    Feature targetFeature;
    if (target != null)
      {
        target.loadCalledFeature(res, thiz);
        if (inh)
          {
            targetFeature = target.calledFeature();  // NYI: What if target is not a call, but, e.g., an IntConst?
          }
        else
          {
            var tt = targetTypeOrConstraint();
            targetFeature = tt.featureOfType();
          }
      }
    else
      { /* search for feature in thiz and outer classes */
        // For an inheritance call, we do not permit call to thiz' features,
        // but only to the outer clazz' features:
        targetFeature = inh ? thiz.outer()
                            : thiz;
      }
    return targetFeature;
  }


  /**
   * Find all the candidates of features that might be called at this point as
   * long as the argument count is ignored.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * @return the map of FeatureName to Features of the found candidates. May be
   * empty. ERROR_MAP in case an error occured and was reported already.
   */
  private Map<FeatureName, Feature> calledFeatureCandidates(Feature targetFeature, Resolution res, Feature thiz)
  {
    if (PRECONDITIONS) require
      (targetFeature != null);

    // are we searching for features called via thiz' inheritance calls?
    Map<FeatureName, Feature> fs = EMPTY_MAP;
    if (target != null)
      {
        res.resolveDeclarations(targetFeature);
        fs = targetFeature.findDeclaredOrInheritedFeatures(name);
      }
    else
      { /* search for feature in thiz and outer classes */
        var fo = targetFeature.findDeclaredInheritedOrOuterFeatures(name, this, null, null);
        if (fo != null)
          {
            target = new This(pos, thiz, fo.outer);
            if (thiz.state() != Feature.State.RESOLVING_INHERITANCE)
              {
                target = ((This)target).resolveTypes(res, thiz);
              }
            fs = fo.features;
          }
      }
    return fs;
  }


  /*-------------------------------------------------------------------*/


  /**
   * if loadCalledFeature is about to fail, try if we can convert this call into
   * a chain of boolean calls:
   *
   * check if we have a call of the form
   *
   *   a < b <= c
   *
   * and convert it to
   *
   *   a < {tmp := b; tmp} && tmp <= c
   */
  private void findChainedBooleans(Resolution res, Feature thiz)
  {
    var cb = chainedBoolTarget(res, thiz);
    if (cb != null && _actuals.size() == 1)
      {
        var rt = new Feature.ResolveTypes(res);
        var b = cb._actuals.get(0);
        b.visit(rt, thiz);
        String tmpName = "#chainedBoolTemp" + (_chainedBoolTempId_++);
        var tmp = new Feature(pos(),
                              Consts.VISIBILITY_INVISIBLE,
                              b.type(),
                              tmpName,
                              thiz);
        Call t1 = new Call(pos(), new Current(pos, thiz.thisType()), tmp, -1);
        Call t2 = new Call(pos(), new Current(pos, thiz.thisType()), tmp, -1);
        var result = new Call(pos(), t2, name, _actuals)
          {
            boolean isChainedBoolRHS() { return true; }
          };
        var as = new Assign(res, pos(), tmp, b, thiz);
        cb._actuals = new List<Expr>(new Block(b.pos(),new List<Stmnt>(as, t1)));
        t1    .visit(rt, thiz);
        result.visit(rt, thiz);
        as    .visit(rt, thiz);
        _actuals = new List<Expr>(result);
        calledFeature_ = Types.resolved.f_bool_AND;
        name = calledFeature_.featureName().baseName();
      }
  }



  /**
   * Predicate that is true if this call is the result of pushArgToTemp in a
   * chain of boolean operators.  This is used for longer chains such as
   *
   *   a < b <= c < d
   *
   * which is first converted into
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * where this returns 'true' for the call 't1 <= c', that in the next steps
   * needs to get 'c' stored into a temporary variable as well.
   */
  boolean isChainedBoolRHS() { return false; }


  /**
   * Does this call a non-generic infix operator?
   */
  boolean isInfixOperator()
  {
    return
      name.startsWith("infix ") &&
      _actuals.size() == 1 &&
      generics == NO_GENERICS;
  }


  /**
   * Check if this call is a chained boolean call of the form
   *
   *   b <= c < d
   *
   * or, if the LHS is also a chained bool
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * and return the part of the LHS that has the term that will need to be
   * stored in a temp variable, 'c', as an argument, i.e., 'b <= c' or 't1 <=
   * c', resp.
   *
   * @return the term whose RHS would have to be stored in a temp variable for a
   * chained boolean call.
   */
  private Call chainedBoolTarget(Resolution res, Feature thiz)
  {
    Call result = null;
    if (Types.resolved != null &&
        targetFeature(res, thiz) == Types.resolved.f_bool &&
        isInfixOperator() &&
        target instanceof Call tc &&
        tc.isInfixOperator())
      {
        result = (tc._actuals.get(0) instanceof Call acc && acc.isChainedBoolRHS())
          ? acc
          : tc;
      }
    return result;
  }


  /*-------------------------------------------------------------------*/


  /**
   * Load all features that are called by this expression.  This is called
   * during state RESOLVING_INHERITANCE for calls in the inherits clauses and
   * during state RESOLVING_TYPES for all other calls.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * NYI: Check if it might make more sense for thiz to be the declared feature
   * instead of the outer feature when processing an inherits clause.
   */
  void loadCalledFeature(Resolution res, Feature thiz)
  {
    if (PRECONDITIONS) require
      (thiz.state() == Feature.State.RESOLVING_INHERITANCE
       ? thiz.outer().state().atLeast(Feature.State.RESOLVED_DECLARATIONS)
       : thiz        .state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    if (calledFeature_ == null &&
        name != Errors.ERROR_STRING)    // If call parsing failed, don't even try
      {
        var targetFeature = targetFeature(res, thiz);
        check
          (Errors.count() > 0 || targetFeature != null);
        if (targetFeature != null && targetFeature != Types.f_ERROR)
          {
            var fs = calledFeatureCandidates(targetFeature, res, thiz);
            FeatureName calledName = FeatureName.get(name, _actuals.size());
            boolean match = false;
            List<Feature> found = new List<Feature>();
            for (var f : fs.entrySet())
              {
                var ff = f.getValue();
                var fn = f.getKey();
                if (fn.equalsExceptId(calledName))  /* an exact match, so use it: */
                  {
                    check
                      (Errors.count() > 0 || !match);
                    calledFeature_ = ff;
                    match = true;
                  }
                else if (!match &&
                         (ff.hasOpenGenericsArgList()               /* actual generics might come from type inference */
                          || forFun                                 /* a fun-declaration "fun a.b.f" */
                          || fn.argCount() == 0 && hasParentheses() /* maybe an implicit call to a Function / Routine, see resolveImmediateFunctionCall() */
                          )
                         )
                  { /* no exact match, but we have a candidate to check later: */
                    calledFeature_ = ff;
                    found.add(ff);
                  }
              }
            if (!match && found.size() > 1)
              {
                FeErrors.ambiguousCallTargets(pos, calledName, found);
              }
            else if (calledFeature_ == null)
              {
                findChainedBooleans(res, thiz);
                if (calledFeature_ == null) // nothing found, so flag error
                  {
                    FeErrors.calledFeatureNotFound(this, calledName, targetFeature);
                  }
              }
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || calledFeature() != null,
       Errors.count() > 0 || target          != null);
  }


  /**
   * After resolveTypes or if calledFeatureKnown(), this can be called to obtain
   * the feature that is called.
   */
  public Feature calledFeature()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || calledFeatureKnown());

    Feature result = calledFeature_ != null ? calledFeature_ : Types.f_ERROR;

    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Is the called feature known? This is the case for calls to anonymous inner
   * features even before resolveTypes is executed. After resolveTypes, this is
   * the case unless there was an error finding the called feature.
   */
  boolean calledFeatureKnown()
  {
    return calledFeature_ != null;
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return calledFeature().isOuterRef();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return (target == null ? "" : target.toString()+".") + (name != null ? name : calledFeature_.featureName().baseName())
      + (generics.isEmpty() ? "" : "<" + generics + ">")
      + (_actuals.isEmpty() ? "" : "(" + _actuals +")")
      + (_select < 0        ? "" : "." + _select);
  }


  /**
   * setTarget
   *
   * @param t
   */
  public void setTarget(Expr t)
  {
    Expr ot = this.target;
    if (ot instanceof Call)
      {
        ((Call)ot).setTarget(t);
      }
    else  if (ot != null)
      {
        throw new Error("target alredy set: old: "+ot+" new: "+t);
      }
    else
      {
        this.target = t;
      }
  }


  /**
   * setBlock
   *
   * @param l
   */
  public void setBlock(List l)
  {
  }


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    return _type;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr visit(FeatureVisitor v, Feature outer)
  {
    if (!generics.isEmpty())
      {
        ListIterator<Type> i = generics.listIterator();
        while (i.hasNext())
          {
            i.set(i.next().visit(v, outer));
          }
      }
    ListIterator<Expr> i = _actuals.listIterator(); // _actuals can change during resolveTypes, so create iterator early
    v.visitActuals
      (() ->
       {
         while (i.hasNext())
           {
             i.set(i.next().visit(v, outer));
           }
       },
       outer);
    if (target != null)
      {
        target = target.visit(v, outer);
      }
    return v.action(this, outer);
  }


  /**
   * Helper function called during resolveTypes to resolve syntactic sugar that
   * allows directly calling a function returned by a call.
   *
   * If this is a normal call (e.g. "f.g") whose result is a function type,
   * ("fun (int a,b) float"), and if g does not take any arguments, syntactic
   * sugar allows an implicit call to Function/Routine.call, i.e., "f.g(3,5)" is
   * a short form of "f.g.call(3,5)".
   *
   * NYI: we could also permit "f.g(x,y)(3,5)" as a short form for
   * "f.g(x,y).call(3,5)" in case g takes arguments.  But this might be too
   * confusing and it would require a change in the grammar.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @param result this in case this was not an immediate call, otherwise qthe
   * resulting cll to Function/Routine.call.
   */
  private Call resolveImmediateFunctionCall(Resolution res, Feature outer)
  {
    Call result = this;
    if (!forFun && // not a call to "b" within an expression of the form "fun a.b", will be handled after syntactic sugar
        _type.isFunType() &&
        calledFeature_ != Types.resolved.f_function && // exclude inherits call in function type
        calledFeature_.arguments.size() == 0 &&
        hasParentheses())
      {
        // a Function: the result is the first generic argument
        var funResultType = _type._generics.isEmpty() ? Types.t_ERROR : _type._generics.getFirst();
        var numFormalArgs = _type._generics.size() - 1;
        result = new Call(pos,
                          "call",
                          NO_GENERICS,
                          _actuals,
                          this /* this becomes target of "call" */,
                          _type.featureOfType().findDeclaredOrInheritedFeature(FeatureName.get("call", _actuals.size())),
                          funResultType)
          .resolveTypes(res, outer);
        _actuals = NO_PARENTHESES;
      }
    return result;
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument after inheritance.
   *
   * The result will be stored in
   * resolvedFormalArgumentTypes[argnum..argnum+result-1].
   *
   * @param res Resolution instance
   *
   * @param argnum the number of this formal argument
   *
   * @param frml the formal argument
   *
   * @return the number of arguments that correspond to argnum after handing
   * down.
   */
  private int handDownFormalArg(Resolution res, int argnum, Feature frml)
  {
    int result = 1;
    Type frmlT = frml.resultType();
    check(frmlT == Types.intern(frmlT));
    Feature declF = calledFeature_.outer();
    Feature heirF = targetTypeOrConstraint().featureOfType();
    if (declF != heirF)
      {
        var a = calledFeature_.handDown(new Type[] { frmlT }, heirF);
        if (a.length != 1)
          {
            // Check that the number or args can only change for the
            // last argument (when it is of an open generic type).  if
            // it would change for other arguments, changing the
            // resolvedFormalArgumentTypes array would invalidate
            // argnum for following arguments.
            check
              (Errors.count() > 0 || argnum == resolvedFormalArgumentTypes.length - 1);
            if (argnum != resolvedFormalArgumentTypes.length -1)
              {
                a = new Type[] { Types.t_ERROR }; /* do not change resolvedFormalArgumentTypes array length */
              }
          }
        addToResolvedFormalArgumentTypes(res, argnum, a, frml);
        result = a.length;
      }
    else
      {
        check
          (Errors.count() > 0 || argnum <= resolvedFormalArgumentTypes.length);

        if (argnum < resolvedFormalArgumentTypes.length)
          {
            resolvedFormalArgumentTypes[argnum] = frmlT;
          }
      }
    return result;
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument from the target type and generics provided to the call.
   *
   * The type(s) will be takenb from
   * resolvedFormalArgumentTypes[argnum..argnum+n-1], the result will be stored
   * in resolvedFormalArgumentTypes[argnum..].
   *
   * @param res Resolution instance
   *
   * @param argnum the number of this formal argument
   *
   * @param n the number of arguments. This might be != 1 if
   * frml.resultType().isOpenGeneric() and this type has been replaced with no
   * or several actual types.
   *
   * @param frml the formal argument
   */
  private void replaceGenericsInFormalArg(Resolution res, int argnum, int n, Feature frml)
  {
    for (int i = 0; i < n; i++)
      {
        check
          (Errors.count() > 0 || argnum + i <= resolvedFormalArgumentTypes.length);

        if (argnum + i < resolvedFormalArgumentTypes.length)
          {
            Type frmlT = resolvedFormalArgumentTypes[argnum + i];

            if (frmlT.isOpenGeneric())
              { // formal arg is open generic, i.e., this expands to 0 or more actual args depending on actual generics for target:
                Generic g = frmlT.genericArgument();
                List<Type> frmlTs = g.replaceOpen(g.formalGenerics().feature() == calledFeature_
                                                  ? generics
                                                  : target.type()._generics);
                addToResolvedFormalArgumentTypes(res, argnum + i, frmlTs.toArray(new Type[frmlTs.size()]), frml);
                i = i + frmlTs.size() - 1;
                n = n + frmlTs.size() - 1;
              }
            else
              {
                frmlT = targetTypeOrConstraint().actualType(frmlT);
                frmlT = frmlT.actualType(calledFeature_, generics);
                frmlT = Types.intern(frmlT);
                resolvedFormalArgumentTypes[argnum + i] = frmlT;
              }
          }
      }
  }


  /**
   * Helper routine for handDownFormalArg and replaceGenericsInFormalArg to
   * extend the resolvedFormalArgumentTypes array.
   *
   * In case frml.resultType().isOpenGeneric(), this will call frml.select() for
   * all the actual types the open generic is replaced by to make sure the
   * corresponding features exist.
   *
   * @param res Resolution instance
   *
   * @param argnum index in resolvedFormalArgumentTypes at which we add new
   * elements
   *
   * @param a the new elements to add to resolvedFormalArgumentTypes
   *
   * @param frml the argument whose type we are resolving.
   */
  private void addToResolvedFormalArgumentTypes(Resolution res, int argnum, Type[] a, Feature frml)
  {
    if (a.length != 1)
      {
        resolvedFormalArgumentTypes = Arrays.copyOf(resolvedFormalArgumentTypes,
                                                    resolvedFormalArgumentTypes.length - 1 + a.length);
      }
    for (int i = 0; i < a.length; i++)
      {
        resolvedFormalArgumentTypes[argnum + i] = a[i];
        if (frml.resultType().isOpenGeneric())
          {
            frml.select(res, i); // make sure features for all actual generics types exist
          }
      }
  }


  /**
   * Helper routine for resolveTypes to resolve the formal argument types of the
   * arguments in this call. Results will be stored in
   * resolvedFormalArgumentTypes array.
   */
  private void resolveFormalArgumentTypes(Resolution res)
  {
    List<Feature> fargs = calledFeature_.arguments;
    resolvedFormalArgumentTypes = fargs.size() == 0 ? Type.NO_TYPES
                                                    : new Type[fargs.size()];
    int count = 0;
    for (Feature frml : fargs)
      {
        check
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        int argnum = count;  // effectively final copy of count
        frml.whenResolvedTypes
          (() ->
           {
             // first, replace generics according to inheritance:
             int n = handDownFormalArg (res, argnum,    frml);
             // next, replace generics given in the target type and in this call
             replaceGenericsInFormalArg(res, argnum, n, frml);
           });
        count++;
      }
    if (POSTCONDITIONS) ensure
      (resolvedFormalArgumentTypes != null);
  }


  /**
   * Helper function for resolveTypes to determine the static result type of
   * this call.
   *
   * In particular, this replaces formal generic types by actual generics
   * provided to this call and it replaces select calls to fields of open
   * genenric type by calls to the actual fields.
   *
   * @param res the resolution instance.
   *
   * @param t the type result type of the called feature, might be open genenric.
   */
  private void resolveType(Resolution res, Type t, Feature outer)
  {
    boolean open = _select != -1;
     if (open != t.isOpenGeneric())
      {
        if (_select == -1)
          {
            Errors.error(pos,
                         "Cannot access value of open generic type",
                         "When calling " + calledFeature_.qualifiedName() + " result type " + t + " is open generic, " +
                         "which cannot be accessed directly.  You might try to access one specific generic parameter " +
                         "by adding '.0', '.1', etc.");
          }
        else
          {
            Errors.error(pos, "Use of selector requires call to feature with open generic type",
                         "In call to " + calledFeature_.qualifiedName() + "\n" +
                         "Selected variant " + name + "." + _select + "\n" +
                         "Type of called feature: " + t);
          }
        t = Types.t_ERROR;
      }
    else
      {
        Type tt = targetTypeOrConstraint();
        if (!open)
          {
            t = tt.actualType(t);
            if (calledFeature_.returnType.isConstructorType() && t != Types.resolved.t_void)
              {  /* specialize t for the target type here */
                t = new Type(t, t._generics, tt);
              }
          }
        else
          {
            List<Type> types = t.genericArgument().replaceOpen(tt._generics);
            int sz = types.size();
            if (_select >= sz)
              {
                Errors.error(pos,
                             "" +
                             (sz > 1  ? "Selector must be in the range of 0.." + (sz - 1) + " for " + sz +" actual generic arguments" :
                              sz == 1 ? "Selector must be 0 for one actual generic argument"
                              : "Selector not permitted since no actual genenric arguments are")+
                             " given for the open generic type",
                             "In call to " + calledFeature_.qualifiedName() + "\n" +
                             "Selected variant " + name + "." + _select + "\n" +
                             "Number of actual generic arguments: " + types.size() + "\n" +
                             "Actual generic arguments: " + (sz == 0 ? "none" : types) + "\n");
                t = Types.t_ERROR;
                calledFeature_ = Types.f_ERROR;
              }
            else
              {
                t = types.get(_select);
                calledFeature_ = calledFeature_.select(res, _select);
              }
            _select = -1;
          }
        t = t.resolve(tt.featureOfType());
      }
    _type = t;
  }


  /**
   * Helper routine for inferGenericsFromArgs: Get the next element from aargs,
   * perform type resolution (which includes possibly replacing it by a
   * different Expr) and return it.
   *
   * @param aargs iterator
   *
   * @param res the resolution instance
   *
   * @param outer the root feature that contains this statement
   */
  private Expr resolveTypeForNextActual(ListIterator<Expr> aargs, Resolution res, Feature outer)
  {
    Expr actual = aargs.next();
    actual = actual.visit(new Feature.ResolveTypes(res), outer);
    aargs.set(actual);
    return actual;
  }


  /**
   * infer the missing generic arguments to this call by inspecting the types of
   * the actual arguments.
   *
   * This is called during resolveTypes, so we have to be careful since type
   * information is not generally available yet.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  private void inferGenericsFromArgs(Resolution res, Feature outer)
  {
    int sz = calledFeature_.generics.list.size();
    Type   [] found         = new Type   [sz]; // The generics that could be inferred
    boolean[] conflict      = new boolean[sz]; // The generics that had conflicting types
    String [] foundAt       = new String [sz]; // detail message for conflicts giving types and their location
    List<Type> inferredOpen = null;  // if open generics could be inferred, these are the actual open generics
    ListIterator<Expr> aargs = _actuals.listIterator();
    int count = 1; // argument count, for error messages
    for (Feature frml : calledFeature_.arguments)
      {
        check
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        Type t = frml.resultTypeIfPresent(res, NO_GENERICS);
        if (t.isGenericArgument() && t.genericArgument().feature() == calledFeature_)
          {
            Generic g = t.genericArgument();
            if (g.isOpen())
              {
                inferredOpen = new List<Type>();
                while (aargs.hasNext() && inferredOpen != null)
                  {
                    count++;
                    Expr actual = resolveTypeForNextActual(aargs, res, outer);
                    Type actualType = actual.typeOrNull();
                    if (actualType == null)
                      {
                        actualType = Types.t_ERROR;
                        Errors.error(pos,
                                     "Failed to infer open generic argument type from actual argument.",
                                     "Type of " + Errors.ordinal(count) + " actual argument could not be inferred at " + actual.pos.show());
                      }
                    inferredOpen.add(actualType);
                  }
              }
            else if (aargs.hasNext())
              {
                count++;
                Expr actual = resolveTypeForNextActual(aargs, res, outer);
                Type actualType = actual.typeOrNull();
                if (actualType != null)
                  {
                    int i = g.index();
                    if (found[i] == null || actualType.isAssignableFromOrContainsError(found[i]))
                      {
                        found[i] = actualType;
                      }
                    conflict[i] = conflict[i] || !found[i].isAssignableFromOrContainsError(actualType);
                    foundAt [i] = (foundAt[i] == null ? "" : foundAt[i]) + actualType + " found at " + actual.pos.show() + "\n";
                  }
              }
          }
        else if (aargs.hasNext())
          {
            aargs.next();
            count++;
          }
      }
    generics = new List<Type>();
    List<Generic> missing = new List<Generic>();
    for (Generic g : calledFeature_.generics.list)
      {
        int i = g.index();
        Type t = found[i];
        if (g.isOpen() && inferredOpen != null)
          {
            generics.addAll(inferredOpen);
          }
        else if (g.isOpen() || t == null)
          {
            missing.add(g);
            t = Types.t_ERROR;
          }
        else if (conflict[i])
          {
            Errors.error(pos,
                         "Incompatible types found during type inference for generic arguments",
                         "Types inferred for " + Errors.ordinal(i+1) + " generic argument " + g + ":\n" +
                         foundAt[i]);
            t = Types.t_ERROR;
          }

        if (!g.isOpen())
          {
            generics.add(t);
          }
      }
    if (!missing.isEmpty())
      {
        Errors.error(pos,
                     "Failed to infer actual generic parameters",
                     "In call to " + calledFeature_.qualifiedName() + ", no actual generic parameters are given and inference of the generic parameters failed.\n" +
                     "Expected generic paramters: " + calledFeature_.generics + "\n"+
                     "Type inference failed for generic " + (missing.size() > 1 ? "arguments" : "argument") + " " + missing + "\n");
      }
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Call resolveTypes(Resolution res, Feature outer)
  {
    Call result = this;
    loadCalledFeature(res, outer);
    FormalGenerics.resolve(generics, outer);

    check
      (Errors.count() > 0 || calledFeature_ != null);

    if (calledFeature_ == null)
      {
        _type = Types.t_ERROR;
      }
    else
      {
        if (generics == NO_GENERICS && calledFeature_.generics != FormalGenerics.NONE)
          {
            inferGenericsFromArgs(res, outer);
          }
        if (calledFeature_.generics.errorIfSizeOrTypeDoesNotMatch(generics,
                                                                  pos,
                                                                  "call",
                                                                  "Called feature: "+calledFeature_.qualifiedName()+"\n"))
          {
            Type t = _isTailRecursive ? Types.resolved.t_void // a tail recursive call will not return and execute further
                                      : calledFeature_.resultTypeIfPresent(res, generics);
            if (t == null)
              {
                calledFeature_.whenResolvedTypes
                  (() ->
                   {
                     Type t2 = calledFeature_.resultTypeForTypeInference(pos, res, generics);
                     resolveType(res, t2, calledFeature_.outer());
                   });
              }
            else
              {
                resolveType(res, t, calledFeature_.outer());
                if (_isTailRecursive)
                  {
                    calledFeature_.whenResolvedTypes
                      (() ->
                       {
                         Type t2 = calledFeature_.resultTypeForTypeInference(pos, res, generics);
                         resolveType(res, t2, calledFeature_.outer());
                       });
                  }

                // Convert a call "f.g(a,b)" into "f.g.call(f,g)" in case f.g takes no
                // arguments and returns a Function or Routine
                result = resolveImmediateFunctionCall(res, outer); // NYI: Separate pass? This currently does not work if type was inferred
              }
          }
        else
          {
            _type = Types.t_ERROR;
          }
        resolveFormalArgumentTypes(res);
      }
    return result;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   */
  public void propagateExpectedType(Resolution res, Feature outer)
  {
    if (!forFun && _type != Types.t_ERROR)
      {
        int fsz = resolvedFormalArgumentTypes.length;
        if (_actuals.size() ==  fsz)
          {
            int count = 0;
            ListIterator<Expr> i = _actuals.listIterator();
            while (i.hasNext())
              {
                Expr actl = i.next();
                Type frmlT = resolvedFormalArgumentTypes[count];
                i.set(actl.propagateExpectedType(res, outer, frmlT));
                count++;
              }
          }
        target = target.propagateExpectedType(res, outer, target.typeOrNull());
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(Feature outer)
  {
    if (!forFun && _type != Types.t_ERROR)
      {
        int fsz = resolvedFormalArgumentTypes.length;
        if (_actuals.size() ==  fsz)
          {
            int count = 0;
            ListIterator<Expr> i = _actuals.listIterator();
            while (i.hasNext())
              {
                Expr actl = i.next();
                Type frmlT = resolvedFormalArgumentTypes[count];
                i.set(actl.box(frmlT));
                count++;
              }
          }
      }
  }


  /**
   * perform static type checking, i.e., make sure that in all assignments from
   * actual to formal arguments, the types match.
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes(Feature outer)
  {
    if (forFun)
      { // this is a call to "b" within an expression of the form "fun a.b". In
        // this case, there must be no generics nor actual arguments to "b", the
        // call will be replaced during Function.resolveSyntacticSugar.
        if (_actuals.size() != 0)
          {
            Errors.error(pos,
                         "Function declaration of the form >>fun a.b<< must not provide any actual arguments to b, b is not called here",
                         "Call that followed >>fun<<: " + this + "\n" +
                         "Actual arguments: " + _actuals + "\n");
          }
        if (hasParentheses())
          {
            Errors.error(pos,
                         "Function declaration of the form >>fun a.b<< must not provide any parentheses b(), b is not called here",
                         "Call that followed >>fun<<: " + this + "\n");
          }
      }
    else if (_type != Types.t_ERROR)
      {
        int fsz = resolvedFormalArgumentTypes.length;
        if (_actuals.size() !=  fsz)
          {
            FeErrors.wrongNumberOfActualArguments(this);
          }
        else
          {
            int count = 0;
            for (Expr actl : _actuals)
              {
                Type actlT = actl.type();

                check
                  (actlT == Types.intern(actlT));

                Type frmlT = resolvedFormalArgumentTypes[count];
                if (frmlT != null && !frmlT.isAssignableFromOrContainsError(actlT))
                  {
                    FeErrors.incompatibleArgumentTypeInCall(calledFeature_, count, frmlT, actlT, actl);
                  }
                count++;
              }
          }
        if (calledFeature_.isChoice())
          {
            boolean ok = false;
            if (outer != null && outer.isChoice())
              {
                for (Call p : outer.inherits)
                  {
                    ok = ok || p == this;
                  }
              }
            if (!ok)
              {
                Errors.error(pos,
                             "Cannot call choice feature",
                             "A choice feature is only used as a type, values are created by assignments only.\n"+
                             "Choice feature that is called: " + calledFeature_.qualifiedName() + "\n" +
                             "Declared at " + calledFeature_.pos.show());
              }
          }

        // Check that generics match formal generic constraints
        var fi = calledFeature_.generics.list.iterator();
        var gi = generics.iterator();
        while (fi.hasNext() &&
               gi.hasNext()    ) // NYI: handling of open generic arguments
          {
            var f = fi.next();
            var g = gi.next();
            if (!f.constraint().constraintAssignableFrom(g))
              {
                Errors.error(pos,
                             "Incompatible actual generic parameter",
                             "formal type "+f+"\n"+
                             "actual type "+g+"\n");
              }
          }
      }
  }


  /**
   * Helper function for resolveSyntacticSugar: Create "if cc block else
   * elseBlock" and handle the case that cc is a compile time constant.
   *
   * NYI: move this to If.resolveSyntacticSugar!
   */
  private Expr newIf(Expr cc, Expr block, Expr elseBlock)
  {
    return
      !cc.isCompileTimeConst()     ? new If(pos(), cc, block, elseBlock) :
      cc.getCompileTimeConstBool() ? block : elseBlock;
  }


  /**
   * Syntactic sugar resolution: This does the following:
   *
   *  - convert boolean operations &&, || and : into if-statements
   *  - convert repeated boolean operations ! into identity   // NYI
   *  - perform constant propagation for basic algebraic ops  // NYI
   *  - replace calls to intrinsics that return compile time constants
   *
   * @return a new Expr to replace this call or this if it remains unchanged.
   */
  Expr resolveSyntacticSugar(Resolution res, Feature outer)
  {
    Expr result = this;
    //    if (true) return result;
    if (Errors.count() == 0)
      {
        // convert
        //   a && b into if a b     else false
        //   a || b into if a true  else b
        //   a: b   into if a b     else true
        //   !a     into if a false else true
        if      (calledFeature_ == Types.resolved.f_bool_AND    ) { result = newIf(target, _actuals.get(0), BoolConst.FALSE); }
        else if (calledFeature_ == Types.resolved.f_bool_OR     ) { result = newIf(target, BoolConst.TRUE , _actuals.get(0)); }
        else if (calledFeature_ == Types.resolved.f_bool_IMPLIES) { result = newIf(target, _actuals.get(0), BoolConst.TRUE ); }
        else if (calledFeature_ == Types.resolved.f_bool_NOT    ) { result = newIf(target, BoolConst.FALSE, BoolConst.TRUE ); }

        // intrinsic features for pre- and postconditions
        else if (calledFeature_ == Types.resolved.f_safety      ) { result = BoolConst.get(res._options.fuzionSafety());      }
        else if (calledFeature_ == Types.resolved.f_debug       ) { result = BoolConst.get(res._options.fuzionDebug());       }
        else if (calledFeature_ == Types.resolved.f_debugLevel  ) { result = new IntConst (res._options.fuzionDebugLevel());  }
      }
    return result;
  }


  /**
   * Find used features, i.e., mark all features that are found to be the target of a call as used.
   */
  public void findUsedFeatures(Resolution res)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || calledFeature_ != null);

    var cf = calledFeature_;
    if (cf != null)
      {
        cf.markUsed(res, isDynamic(), pos);
        for (Type t : generics)
          {
            if (!t.isGenericArgument())
              {
                Feature f = t.featureOfType();
                f.markUsed(res, t.pos);  // NYI: needed? If the actual generic type is not called anywhere, maybe it can go
              }
          }
      }
  }


  /**
   * Does this call use dynamic binding. Dynamic binding is used if the called
   * feature uses dynamic binding and the target is not Current. In case the
   * target is current, this call will be specialized to avoid dynamic binding.
   */
  public boolean isDynamic()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || calledFeature_ != null);

    var cf = calledFeature_;
    return
      cf != null &&
      cf.isDynamic() &&
      !(target instanceof Current);
  }

}

/* end of file */
