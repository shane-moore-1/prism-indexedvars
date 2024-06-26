/* Generated By:JavaCC: Do not edit this line. PrismParserConstants.java */
package parser;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface PrismParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int WHITESPACE = 1;
  /** RegularExpression Id. */
  int COMMENT = 2;
  /** RegularExpression Id. */
  int A = 3;
  /** RegularExpression Id. */
  int BOOL = 4;
  /** RegularExpression Id. */
  int CLOCK = 5;
  /** RegularExpression Id. */
  int CONST = 6;
  /** RegularExpression Id. */
  int CTMC = 7;
  /** RegularExpression Id. */
  int C = 8;
  /** RegularExpression Id. */
  int DEFER = 9;
  /** RegularExpression Id. */
  int DOUBLE = 10;
  /** RegularExpression Id. */
  int DTMC = 11;
  /** RegularExpression Id. */
  int E = 12;
  /** RegularExpression Id. */
  int ENDINIT = 13;
  /** RegularExpression Id. */
  int ENDINVARIANT = 14;
  /** RegularExpression Id. */
  int ENDMODULE = 15;
  /** RegularExpression Id. */
  int ENDREWARDS = 16;
  /** RegularExpression Id. */
  int ENDSYSTEM = 17;
  /** RegularExpression Id. */
  int FALSE = 18;
  /** RegularExpression Id. */
  int FORMULA = 19;
  /** RegularExpression Id. */
  int FILTER = 20;
  /** RegularExpression Id. */
  int FUNC = 21;
  /** RegularExpression Id. */
  int F = 22;
  /** RegularExpression Id. */
  int GLOBAL = 23;
  /** RegularExpression Id. */
  int G = 24;
  /** RegularExpression Id. */
  int INIT = 25;
  /** RegularExpression Id. */
  int INVARIANT = 26;
  /** RegularExpression Id. */
  int I = 27;
  /** RegularExpression Id. */
  int INT = 28;
  /** RegularExpression Id. */
  int LABEL = 29;
  /** RegularExpression Id. */
  int MAX = 30;
  /** RegularExpression Id. */
  int MDP = 31;
  /** RegularExpression Id. */
  int MIN = 32;
  /** RegularExpression Id. */
  int MODULE = 33;
  /** RegularExpression Id. */
  int X = 34;
  /** RegularExpression Id. */
  int NONDETERMINISTIC = 35;
  /** RegularExpression Id. */
  int OTHERWISE = 36;
  /** RegularExpression Id. */
  int PMAX = 37;
  /** RegularExpression Id. */
  int PMIN = 38;
  /** RegularExpression Id. */
  int P = 39;
  /** RegularExpression Id. */
  int PROBABILISTIC = 40;
  /** RegularExpression Id. */
  int PROB = 41;
  /** RegularExpression Id. */
  int PTA = 42;
  /** RegularExpression Id. */
  int RATE = 43;
  /** RegularExpression Id. */
  int REWARDS = 44;
  /** RegularExpression Id. */
  int RESTRICT = 45;
  /** RegularExpression Id. */
  int RMAX = 46;
  /** RegularExpression Id. */
  int RMIN = 47;
  /** RegularExpression Id. */
  int R = 48;
  /** RegularExpression Id. */
  int S = 49;
  /** RegularExpression Id. */
  int STOCHASTIC = 50;
  /** RegularExpression Id. */
  int SYSTEM = 51;
  /** RegularExpression Id. */
  int TILDE = 52;
  /** RegularExpression Id. */
  int TRUE = 53;
  /** RegularExpression Id. */
  int U = 54;
  /** RegularExpression Id. */
  int W = 55;
  /** RegularExpression Id. */
  int NOT = 56;
  /** RegularExpression Id. */
  int AND = 57;
  /** RegularExpression Id. */
  int OR = 58;
  /** RegularExpression Id. */
  int IMPLIES = 59;
  /** RegularExpression Id. */
  int IFF = 60;
  /** RegularExpression Id. */
  int RARROW = 61;
  /** RegularExpression Id. */
  int COLON = 62;
  /** RegularExpression Id. */
  int SEMICOLON = 63;
  /** RegularExpression Id. */
  int COMMA = 64;
  /** RegularExpression Id. */
  int DOTS = 65;
  /** RegularExpression Id. */
  int LPARENTH = 66;
  /** RegularExpression Id. */
  int RPARENTH = 67;
  /** RegularExpression Id. */
  int LBRACKET = 68;
  /** RegularExpression Id. */
  int RBRACKET = 69;
  /** RegularExpression Id. */
  int DLBRACKET = 70;
  /** RegularExpression Id. */
  int DRBRACKET = 71;
  /** RegularExpression Id. */
  int LBRACE = 72;
  /** RegularExpression Id. */
  int RBRACE = 73;
  /** RegularExpression Id. */
  int EQ = 74;
  /** RegularExpression Id. */
  int NE = 75;
  /** RegularExpression Id. */
  int LT = 76;
  /** RegularExpression Id. */
  int GT = 77;
  /** RegularExpression Id. */
  int DLT = 78;
  /** RegularExpression Id. */
  int DGT = 79;
  /** RegularExpression Id. */
  int LE = 80;
  /** RegularExpression Id. */
  int GE = 81;
  /** RegularExpression Id. */
  int PLUS = 82;
  /** RegularExpression Id. */
  int MINUS = 83;
  /** RegularExpression Id. */
  int TIMES = 84;
  /** RegularExpression Id. */
  int DIVIDE = 85;
  /** RegularExpression Id. */
  int PRIME = 86;
  /** RegularExpression Id. */
  int RENAME = 87;
  /** RegularExpression Id. */
  int QMARK = 88;
  /** RegularExpression Id. */
  int DQUOTE = 89;
  /** RegularExpression Id. */
  int REG_INT = 90;
  /** RegularExpression Id. */
  int REG_DOUBLE = 91;
  /** RegularExpression Id. */
  int REG_IDENT = 92;
  /** RegularExpression Id. */
  int PREPROC = 93;
  /** RegularExpression Id. */
  int LEXICAL_ERROR = 94;

  /** Lexical state. */
  int DEFAULT = 0;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "<WHITESPACE>",
    "<COMMENT>",
    "\"A\"",
    "\"bool\"",
    "\"clock\"",
    "\"const\"",
    "\"ctmc\"",
    "\"C\"",
    "\"defer\"",
    "\"double\"",
    "\"dtmc\"",
    "\"E\"",
    "\"endinit\"",
    "\"endinvariant\"",
    "\"endmodule\"",
    "\"endrewards\"",
    "\"endsystem\"",
    "\"false\"",
    "\"formula\"",
    "\"filter\"",
    "\"func\"",
    "\"F\"",
    "\"global\"",
    "\"G\"",
    "\"init\"",
    "\"invariant\"",
    "\"I\"",
    "\"int\"",
    "\"label\"",
    "\"max\"",
    "\"mdp\"",
    "\"min\"",
    "\"module\"",
    "\"X\"",
    "\"nondeterministic\"",
    "\"otherwise\"",
    "\"Pmax\"",
    "\"Pmin\"",
    "\"P\"",
    "\"probabilistic\"",
    "\"prob\"",
    "\"pta\"",
    "\"rate\"",
    "\"rewards\"",
    "\"restrict\"",
    "\"Rmax\"",
    "\"Rmin\"",
    "\"R\"",
    "\"S\"",
    "\"stochastic\"",
    "\"system\"",
    "\"~\"",
    "\"true\"",
    "\"U\"",
    "\"W\"",
    "\"!\"",
    "\"&\"",
    "\"|\"",
    "\"=>\"",
    "\"<=>\"",
    "\"->\"",
    "\":\"",
    "\";\"",
    "\",\"",
    "\"..\"",
    "\"(\"",
    "\")\"",
    "\"[\"",
    "\"]\"",
    "\"[[\"",
    "\"]]\"",
    "\"{\"",
    "\"}\"",
    "\"=\"",
    "\"!=\"",
    "\"<\"",
    "\">\"",
    "\"<<\"",
    "\">>\"",
    "\"<=\"",
    "\">=\"",
    "\"+\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"\\\'\"",
    "\"<-\"",
    "\"?\"",
    "\"\\\"\"",
    "<REG_INT>",
    "<REG_DOUBLE>",
    "<REG_IDENT>",
    "<PREPROC>",
    "<LEXICAL_ERROR>",
  };

}
