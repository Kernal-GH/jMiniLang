package priv.bajdcc.lexer.regex;

import java.util.HashMap;
import java.util.HashSet;

import priv.bajdcc.lexer.stringify.RegexToString;
import priv.bajdcc.lexer.token.TokenUtility;
import priv.bajdcc.lexer.token.MetaType;
import priv.bajdcc.lexer.automata.dfa.DFA;
import priv.bajdcc.lexer.error.RegexException;
import priv.bajdcc.lexer.error.RegexException.RegexError;

/**
 * ## �������ʽ�������� ##<br/>
 * ���������﷨��<br/>
 * �﷨ͬһ����������ʽ��ֻ��̰��ģʽ��û��ǰ/����ƥ�䣬 û�в����ܣ�������ƥ�䡣
 * 
 * @author bajdcc
 */
public class Regex extends RegexStringIterator {

	/**
	 * �Ƿ�Ϊ����ģʽ����ӡ��Ϣ��
	 */
	private boolean m_bDebug = false;

	/**
	 * ����ʽ�������
	 */
	private IRegexComponent m_Expression = null;

	/**
	 * DFA
	 */
	private DFA m_DFA = null;

	/**
	 * DFA״̬ת����
	 */
	private int[][] m_Transition = null;

	/**
	 * ��̬��
	 */
	private HashSet<Integer> m_FinalStatus = new HashSet<Integer>();

	/**
	 * �ַ������
	 */
	private CharacterMap m_Map = null;

	/**
	 * �ַ������˽ӿ�
	 */
	private IRegexStringFilter m_Filter = null;

	public void setFilter(IRegexStringFilter filter) {
		m_Filter = filter;
	}

	private static HashMap<Character, MetaType> g_mapMeta = new HashMap<Character, MetaType>();

	static {
		MetaType[] metaTypes = new MetaType[] { MetaType.LPARAN,
				MetaType.RPARAN, MetaType.STAR, MetaType.PLUS, MetaType.QUERY,
				MetaType.CARET, MetaType.LSQUARE, MetaType.RSQUARE,
				MetaType.BAR, MetaType.ESCAPE, MetaType.DASH, MetaType.LBRACE,
				MetaType.RBRACE, MetaType.COMMA, MetaType.DOT,
				MetaType.NEW_LINE, MetaType.CARRIAGE_RETURN, MetaType.BACKSPACE };
		for (MetaType meta : metaTypes) {
			g_mapMeta.put(meta.getChar(), meta);
		}
	}

	public Regex(String pattern) throws RegexException {
		this(pattern, false);
	}

	public Regex(String pattern, boolean debug) throws RegexException {
		super(pattern);
		m_bDebug = debug;
		compile();
	}

	/**
	 * ## �������ʽ ##<br/>
	 * 
	 * @throws RegexException
	 */
	private void compile() throws RegexException {
		translate();
		/* String->AST */
		m_Expression = analysis(MetaType.END.getChar(), MetaType.END);
		if (m_bDebug) {
			System.out.println("#### �������ʽ�﷨�� ####");
			System.out.println(toString());
		}
		/* AST->ENFA->NFA->DFA */
		m_DFA = new DFA(m_Expression, m_bDebug);
		/* DFA Transfer Table */
		buildTransition();
	}

	/**
	 * ����DFA״̬ת����
	 */
	private void buildTransition() {
		/* �ַ�����ӳ��� */
		m_Map = m_DFA.getCharacterMap();
		/* DFA״̬ת�Ʊ� */
		m_Transition = m_DFA.buildTransition(m_FinalStatus);
	}

	/**
	 * ƥ���㷨��DFA״̬����
	 * 
	 * @param iterator
	 *            �ַ��������ӿ�
	 * @param attr
	 *            �����ƥ���ַ���
	 * @return �Ƿ�ƥ��ɹ�
	 */
	public boolean match(IRegexStringIterator iterator,
			IRegexStringAttribute attr) {
		/* ʹ��ȫ���ַ�ӳ��� */
		int[] charMap = m_Map.getStatus();
		/* ���浱ǰλ�� */
		iterator.snapshot();
		/* ��ǰ״̬ */
		int status = 0;
		/* �ϴξ�������̬ */
		int lastFinalStatus = -1;
		/* �ϴξ�������̬λ�� */
		int lastIndex = -1;
		/* ���ƥ���ַ��� */
		StringBuilder sb = new StringBuilder();
		for (;;) {
			if (m_FinalStatus.contains(status)) {// ������̬
				if (attr.getGreedMode()) {// ̰��ģʽ
					if (lastFinalStatus == -1) {
						iterator.snapshot();// ����λ��
					} else {
						iterator.cover();// ����λ��
					}
					lastFinalStatus = status;// ��¼�ϴ�״̬
					lastIndex = sb.length();
				} else {// ��̰��ģʽ����ƥ�����
					iterator.discard();// ƥ��ɹ�������λ��
					attr.setResult(sb.toString());
					return true;
				}
			}
			char local = 0;
			boolean skipStore = false;// ȡ���洢��ǰ�ַ�
			/* ��õ�ǰ�ַ� */
			if (m_Filter != null) {
				RegexStringIteratorData data = m_Filter.filter(iterator);// ����
				local = data.m_chCurrent;
				skipStore = data.m_kMeta == MetaType.NULL;
			} else {
				if (!iterator.available()) {
					local = 0;
				} else {
					local = iterator.current();
					iterator.next();
				}
			}
			/* �洢�ַ� */
			if (!skipStore) {
				sb.append(local);
			}
			/* ����ַ��������� */
			int charClass = charMap[local];
			/* ״̬ת�� */
			int refer = -1;
			if (charClass != -1) {// ������Ч������ת��
				refer = m_Transition[status][charClass];
			}
			if (refer == -1) {// ʧ��
				iterator.restore();
				if (lastFinalStatus == -1) {// ƥ��ʧ��
					return false;
				} else {// ʹ���ϴξ�������̬ƥ����
					iterator.discard();// ƥ��ɹ�������λ��
					attr.setResult(sb.substring(0, lastIndex));
					return true;
				}
			} else {
				status = refer;// ����״̬
			}
		}
	}

	private IRegexComponent analysis(char terminal, MetaType meta)
			throws RegexException {
		Constructure sequence = new Constructure(false);// ���������Դ洢����ʽ
		Constructure branch = null;// ������֧�Դ洢'|'�ͱ���ʽ���Ƿ��Ƿ�֧�д�Ԥ��
		Constructure result = sequence;

		for (;;) {
			if ((m_Data.m_kMeta == meta && m_Data.m_chCurrent == terminal)) {// �����ַ�
				if (m_Data.m_iIndex == 0) {// ����ʽΪ��
					err(RegexError.NULL);
				} else if (sequence.m_arrComponents.isEmpty()) {// ����Ϊ��
					err(RegexError.INCOMPLETE);
				} else {
					next();
					break;// ������ֹ
				}
			} else if (m_Data.m_kMeta == MetaType.END) {
				err(RegexError.INCOMPLETE);
			}
			IRegexComponent expression = null;// ��ǰ����ֵ�ı���ʽ
			switch (m_Data.m_kMeta) {
			case BAR:// '|'
				next();
				if (sequence.m_arrComponents.isEmpty())// �ڴ�֮ǰû�д洢����ʽ (|...)
				{
					err(RegexError.INCOMPLETE);
				} else {
					if (branch == null) {// ��֧Ϊ�գ�������֧
						branch = new Constructure(true);
						branch.m_arrComponents.add(sequence);// ���½��ķ�֧�����������ǰ����
						result = branch;
					}
					sequence = new Constructure(false);// �½�һ������
					branch.m_arrComponents.add(sequence);
				}
				break;
			case LPARAN:// '('
				next();
				expression = analysis(MetaType.RPARAN.getChar(),
						MetaType.RPARAN);// �ݹ����
				break;
			default:
				break;
			}

			if (expression == null) {// ��ǰ���Ǳ���ʽ������Ϊ�ַ�
				Charset charset = new Charset();// ��ǰ���������ַ���
				expression = charset;
				switch (m_Data.m_kMeta) {
				case ESCAPE:// '\\'
					next();
					escape(charset, true);// ����ת��
					break;
				case DOT:// '.'
					m_Data.m_kMeta = MetaType.CHARACTER;
					escape(charset, true);
					break;
				case LSQUARE: // '['
					next();
					range(charset);
					break;
				case END: // '\0'
					return result;
				default:
					if (!charset.addChar(m_Data.m_chCurrent)) {
						err(RegexError.RANGE);
					}
					next();
					break;
				}
			}

			Repetition rep = null;// ѭ��
			switch (m_Data.m_kMeta) {
			case QUERY:// '?'
				next();
				rep = new Repetition(expression, 0, 1);
				sequence.m_arrComponents.add(rep);
				break;
			case PLUS:// '+'
				next();
				rep = new Repetition(expression, 1, -1);
				sequence.m_arrComponents.add(rep);
				break;
			case STAR:// '*'
				next();
				rep = new Repetition(expression, 0, -1);
				sequence.m_arrComponents.add(rep);
				break;
			case LBRACE: // '{'
				next();
				rep = new Repetition(expression, 0, -1);
				quantity(rep);
				sequence.m_arrComponents.add(rep);
				break;
			default:
				sequence.m_arrComponents.add(expression);
				break;
			}
		}

		return result;
	}

	/**
	 * ����ת���ַ�
	 * 
	 * @param charset
	 *            �ַ���
	 * @param extend
	 *            �Ƿ�֧����չ��\d \w��
	 * @throws RegexException
	 */
	private void escape(Charset charset, boolean extend) throws RegexException {
		char ch = m_Data.m_chCurrent;
		if (m_Data.m_kMeta == MetaType.CHARACTER) {// �ַ�
			next();
			if (extend) {
				if (TokenUtility.isUpperLetter(ch) || ch == '.') {
					charset.m_bReverse = true;// ��д��ȡ��
				}
				char cl = Character.toLowerCase(ch);
				switch (cl) {
				case 'd':// ����
					charset.addRange('0', '9');
					return;
				case 'a':// ��ĸ
					charset.addRange('a', 'z');
					charset.addRange('A', 'Z');
					return;
				case 'w':// ��ʶ��
					charset.addRange('a', 'z');
					charset.addRange('A', 'Z');
					charset.addRange('0', '9');
					charset.addChar('_');
					return;
				case 's':// �հ��ַ�
					charset.addChar('\r');
					charset.addChar('\n');
					charset.addChar('\t');
					charset.addChar('\b');
					charset.addChar('\f');
					charset.addChar(' ');
					return;
				default:
					break;
				}
			}
			if (TokenUtility.isLetter(ch)) {// ���Ϊ��ĸ
				ch = m_Utility.fromEscape(ch, RegexError.ESCAPE);
				if (!charset.addChar(ch)) {
					err(RegexError.RANGE);
				}
			}
		} else if (m_Data.m_kMeta == MetaType.END) {
			err(RegexError.INCOMPLETE);
		} else {// �����ַ���ת��
			next();
			if (!charset.addChar(ch)) {
				err(RegexError.RANGE);
			}
		}
	}

	/**
	 * �����ַ�����
	 * 
	 * @param charset
	 *            �ַ���
	 * @throws RegexException
	 */
	private void range(Charset charset) throws RegexException {
		if (m_Data.m_kMeta == MetaType.CARET) {// '^'ȡ��
			next();
			charset.m_bReverse = true;
		}
		while (m_Data.m_kMeta != MetaType.RSQUARE) {// ']'
			if (m_Data.m_kMeta == MetaType.CHARACTER) {
				character(charset);
				char lower = m_Data.m_chCurrent; // lower bound
				next();
				if (m_Data.m_kMeta == MetaType.DASH) {// '-'
					next();
					character(charset);
					char upper = m_Data.m_chCurrent; // upper bound
					next();
					if (lower > upper) {// check bound
						err(RegexError.RANGE);
					}
					if (!charset.addRange(lower, upper)) {
						err(RegexError.RANGE);
					}
				} else {
					if (!charset.addChar(lower)) {
						err(RegexError.RANGE);
					}
				}
			} else if (m_Data.m_kMeta == MetaType.ESCAPE) {
				next();
				escape(charset, false);
			} else {
				next();
				charset.addChar(m_Data.m_chCurrent);
			}
		}
		next();
	}

	/**
	 * �����ַ�
	 * 
	 * @param charset
	 *            �ַ���
	 * @throws RegexException
	 */
	private void character(Charset charset) throws RegexException {
		if (m_Data.m_kMeta == MetaType.ESCAPE) {// '\\'
			next();
			escape(charset, false);
		} else if (m_Data.m_kMeta == MetaType.END) {// '\0'
			err(RegexError.INCOMPLETE);
		} else if (m_Data.m_kMeta != MetaType.CHARACTER
				&& m_Data.m_kMeta != MetaType.DASH) {
			err(RegexError.CTYPE);
		}
	}

	/**
	 * ��������
	 * 
	 * @throws RegexException
	 */
	private void quantity(Repetition rep) throws RegexException {
		int lower, upper;
		lower = upper = digit();// ѭ���½�
		if (lower == -1) {
			err(RegexError.BRACE);
		}
		if (m_Data.m_kMeta == MetaType.COMMA) {// ','
			next();
			if (m_Data.m_kMeta == MetaType.RBRACE) {// '}'
				upper = -1;// �Ͻ�Ϊ�����
			} else {
				upper = digit();// �õ�ѭ���Ͻ�
				if (upper == -1) {
					err(RegexError.BRACE);
				}
			}
		}
		if (upper != -1 && upper < lower) {
			err(RegexError.RANGE);
		}
		expect(MetaType.RBRACE, RegexError.BRACE);
		rep.m_iLowerBound = lower;
		rep.m_iUpperBound = upper;
	}

	/**
	 * ʮ��������ת��
	 * 
	 * @return ����
	 */
	private int digit() {
		int index = m_Data.m_iIndex;
		while (Character.isDigit(m_Data.m_chCurrent)) {
			next();
		}
		try {
			return Integer.valueOf(
					m_strContext.substring(index, m_Data.m_iIndex), 10);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	@Override
	protected void transform() {
		if (g_mapMeta.containsKey(m_Data.m_chCurrent)) {
			m_Data.m_kMeta = g_mapMeta.get(m_Data.m_chCurrent);// �����ַ�
		} else {
			m_Data.m_kMeta = MetaType.CHARACTER;// һ���ַ�
		}
	}

	@Override
	public String toString() {
		RegexToString alg = new RegexToString();// ����ʽ�����л��㷨��ʼ��
		m_Expression.visit(alg);// ������
		return alg.toString();
	}

	/**
	 * ��ȡ�ַ���������
	 */
	public String getStatusString() {
		return m_DFA.getStatusString();
	}

	/**
	 * ��ȡNFA����
	 */
	public String getNFAString() {
		return m_DFA.getNFAString();
	}
}