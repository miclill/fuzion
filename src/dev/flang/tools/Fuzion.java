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
 * Source of class Fuzion
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.be.c.C;
import dev.flang.be.c.COptions;

import dev.flang.be.interpreter.Intrinsics;
import dev.flang.be.interpreter.Interpreter;

import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

import dev.flang.me.MiddleEnd;

import dev.flang.opt.Optimizer;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Errors;


/**
 * Fuzion is the main class of the Fuzion interpreter and compiler.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class Fuzion extends Tool
{

  /*----------------------------  constants  ----------------------------*/

  static String _binaryName_ = null;


  /**
   * Fuzion Backends:
   */
  static enum Backend
  {
    interpreter("-interpreter"),
    c          ("-c")
    {
      String usage()
      {
        return "[-o=<file>] ";
      }
      boolean handleOption(String o)
      {
        boolean result = false;
        if (o.startsWith("-o="))
          {
            _binaryName_ = o.substring(3);
            result = true;
          }
        return result;
      }
    }
    ,
    java       ("-java"),
    classes    ("-classes"),
    llvm       ("-llvm"),
    undefined;

    /**
     * the command line argument corresponding to this backend
     */
    private final String _arg;

    /**
     * Construct undefined backend
     */
    Backend()
    {
      _arg = null;
    }

    /**
     * Construct normal Backend option
     *
     * @param arg the command line arg to enable this backend
     */
    Backend(String arg)
    {
      if (PRECONDITIONS) require
        (arg != null && arg.startsWith("-"));

      _arg = arg;
      if (usage() == "")
        {
          _allBackendArgs_.append(_allBackendArgs_.length() == 0 ? "" : "|").append(arg);
        }
      else
        {
          _allBackendExtraUsage_.append("       @CMD@ " + _arg + " " + usage() + STD_OPTIONS + " --or--\n");
        }
      _allBackends_.put(arg, this);
    }


    /**
     * Does this backend handle a specific option? If so, must return true.
     */
    boolean handleOption(String o)
    {
      return false;
    }

    /**
     * Usage string for the specific options handled by this backend. "" if
     * none.  Must end with " " otherwise.
     */
    String usage()
    {
      return "";
    }
  }

  static StringBuilder _allBackendArgs_ = new StringBuilder();
  static StringBuilder _allBackendExtraUsage_ = new StringBuilder();
  static TreeMap<String, Backend> _allBackends_ = new TreeMap<>();

  static { var __ = Backend.undefined; } /* make sure _allBackendArgs_ is initialized */


  /*----------------------------  variables  ----------------------------*/


  /**
   * Flag to enable intrinsic functions such as fuzion.java.callVirtual. These are
   * not allowed if run in a web playground.
   */
  boolean _enableUnsafeIntrinsics = Boolean.getBoolean("fuzion.enableUnsafeIntrinsics");


  /**
   * Default result of debugLevel:
   */
  int _debugLevel = Integer.getInteger("fuzion.debugLevel", 1);


  /**
   * List of modules added using '-module'.
   */
  List<String> _modules = new List<>();


  /**
   * Default result of safety:
   */
  boolean _safety = Boolean.valueOf(System.getProperty("fuzion.safety", "true"));


  /**
   * Read input from stdin instead of file?
   */
  boolean _readStdin = false;


  /**
   * name of main features .
   */
  String  _main = null;


  /**
   * Desired backend.
   */
  Backend _backend = Backend.undefined;


  /*--------------------------  static methods  -------------------------*/


  /**
   * main the main method
   *
   * @param args the command line arguments.  One argument is
   * currently supported: the main feature name.
   */
  public static void main(String[] args)
  {
    new Fuzion().run(args);
  }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the Fuzion class
   */
  private Fuzion()
  {
    super("fz");
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The standard options that come with every tool.  May be redefined to add
   * more standard options to be used in different configurations.
   *
   * @param xtra include extra options such as -Xhelp, -XjavaPof, etc.
   */
  protected String STANDARD_OPTIONS(boolean xtra)
  {
    return super.STANDARD_OPTIONS(xtra);
  }


  /**
   * The basic usage, using STD_OPTIONS as a placeholder for standard
   * options.
   */
  protected String USAGE0()
  {
    return
      "Usage: " + _cmd + " [-h|--help] [" + _allBackendArgs_ + "] " + STD_OPTIONS + "[-modules={<m>,..} [-debug[=<n>]] [-safety=(on|off)] [-unsafeIntrinsics=(on|off)] (<main> | <srcfile>.fz | -)  --or--\n" +
      _allBackendExtraUsage_.toString().replace("@CMD@", _cmd) +
      "       " + _cmd + " -pretty " + STD_OPTIONS + " ({<file>} | -)\n" +
      "       " + _cmd + " -latex " + STD_OPTIONS + "\n";
  }


  /**
   * Parse the given command line args and create a runnable to run the
   * corresponding tool.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the selected tool.
   */
  public Runnable parseArgs(String[] args)
  {
    if (args.length >= 1 && args[0].equals("-pretty"))
      {
        return parseArgsPretty(args);
      }
    else if (args.length >= 1 && args[0].equals("-latex"))
      {
        return parseArgsLatex(args);
      }
    else
      {
        return parseArgsForBackend(args);
      }
  }


  /**
   * Parse the given command line args for the pretty printer and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the pretty printer.
   */
  private Runnable parseArgsPretty(String[] args)
  {
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (!parseGenericArg(a) &&
            !a.equals("-pretty")  // ignore, we know this already
            )
          {
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else
              {
                sourceFiles.add(a);
              }
          }
      }
    if (sourceFiles.isEmpty() && !_readStdin)
      {
        fatal("no source files given");
      }
    else if (!sourceFiles.isEmpty() && _readStdin)
      {
        fatal("cannot process both, stdin input '-' and a list of source files");
      }
    return () ->
      {
        if (_readStdin)
          {
            new Pretty();
          }
        else
          {
            for (var s : sourceFiles)
              {
                new Pretty(s);
              }
          }
      };
  }


  /**
   * Parse the given command line args for the pretty printer and create a
   * runnable that executes it.  System.exit() in case of error or -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run the pretty printer.
   */
  private Runnable parseArgsLatex(String[] args)
  {
    var sourceFiles = new List<String>();
    for (var a : args)
      {
        if (!parseGenericArg(a) &&
            !a.equals("-latex")   // ignore, we know this already
            )
          {
            unknownArg(a);
          }
      }
    return () ->
      {
        new Latex();
      };
  }


  /**
   * Parse the given command line args to run Fuzion to create or execute code.
   * Return a runnable that runs fuzion.  System.exit() in case of error or
   * -help.
   *
   * @param args the command line arguments
   *
   * @return a Runnable to run fuzion.
   */
  private Runnable parseArgsForBackend(String[] args)
  {
    for (var a : args)
      {
        if (!parseGenericArg(a))
          {
            if (a.equals("-"))
              {
                _readStdin = true;
              }
            else if (_allBackends_.containsKey(a))
              {
                if (_backend != Backend.undefined)
                  {
                    fatal("arguments must specify at most one backend, found '" + _backend._arg + "' and '" + a + "'");
                  }
                _backend = _allBackends_.get(a);
              }
            else if (a.startsWith("-modules="         )) { _modules.addAll(parseStringListArg(a));              }
            else if (a.matches("-debug(=\\d+|)"       )) { _debugLevel             = parsePositiveIntArg(a, 1); }
            else if (a.startsWith("-safety="          )) { _safety                 = parseOnOffArg(a);          }
            else if (a.startsWith("-unsafeIntrinsics=")) { _enableUnsafeIntrinsics = parseOnOffArg(a);          }
            else if (_backend.handleOption(a))
              {
              }
            else if (a.startsWith("-"))
              {
                unknownArg(a);
              }
            else if (_main != null)
              {
                fatal("several main feature names provided: '" + _main + "', '" + a + "'");
              }
            else
              {
                _main = a;
              }
          }
      }
    if (_main == null && !_readStdin)
      {
        fatal("missing main feature name in command line args");
      }
    if (_main != null && _readStdin)
      {
        fatal("cannot process main feature name together with stdin input");
      }
    if (_backend == Backend.undefined)
      {
        _backend = Backend.interpreter;
      }
    return () ->
      {
        var options = new FrontEndOptions(_verbose,
                                          _modules,
                                          _debugLevel,
                                          _safety,
                                          _readStdin,
                                          _main);
        if (_backend == Backend.c)
          {
            options.setTailRec();
          }
        long jvmStartTime = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long prepTime = System.currentTimeMillis();
        var mir = new FrontEnd(options).createMIR();
        long feTime = System.currentTimeMillis();
        var air = new MiddleEnd(options, mir).air();
        long meTime = System.currentTimeMillis();
        var fuir = new Optimizer(options, air).fuir(_backend != Backend.interpreter);
        long irTime = System.currentTimeMillis();
        switch (_backend)
          {
          case interpreter:
            {
              Intrinsics.ENABLE_UNSAFE_INTRINSICS = _enableUnsafeIntrinsics;  // NYI: Add to Fuzion IR or BE Config
              var in = new Interpreter(fuir);
              irTime = System.currentTimeMillis();
              in.run(); break;
            }
          case c          : new C(new COptions(options, _binaryName_), fuir).compile(); break;
          default         : Errors.fatal("backend '" + _backend + "' not supported yet"); break;
          }
        long beTime = System.currentTimeMillis();

        beTime -= irTime;
        irTime -= meTime;
        meTime -= feTime;
        feTime -= prepTime;
        prepTime -= jvmStartTime;
        options.verbosePrintln(1, "Elapsed time for phases: prep "+prepTime+"ms, fe "+feTime+"ms, me "+meTime+"ms, ir "+irTime+"ms, be "+beTime+"ms");
      };
  }

}

/* end of file */
