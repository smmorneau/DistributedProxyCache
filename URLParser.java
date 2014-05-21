import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author smmorneau
 *
 * Parses a string, identifying valid URLs and their components.
 *
 */
public class URLParser 
{
	/* Example Regular Expressions
	 * 
	 * From http://labs.apache.org/webarch/uri/rfc/rfc3986.html
	 * ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
	 * 
	 * protocol:	 ^([a-zA-Z][\\w+-.]*)://		required, starts with letter	
	 * domain:	 ([^/?#]+)					required, does not contain /?#
	 * resource:	 (/[^?#]*)?					starts with /, does not contain ?#
	 * query:	 (?:\\?([^#]*))?				starts with ?, does not contain #
	 * fragment:	 (?:#(.*))?$					starts with #, everything until end
	 */
	private static final String pregex = "(http://)";
	private static final String dregex = "([^/?#]+)";
	private static final String rregex = "(/[^?#]*)?(.htm[l]?)?";

	private static final String regex = 
			pregex + dregex + rregex;

	public final String url;		// http://docs.python.org/library/string.html?highlight=string#module-string
	public final String protocol;	// (req) http
	public final String domain;		// (req) docs.python.org
	public final String resource;	// (opt) /library/string.html

	public final boolean valid;

	public URLParser(String url)
	{
		this.url = url;

		Pattern p = Pattern.compile(regex);
		Matcher m = null;

		if(url != null)
			m = p.matcher(url);

		if(m == null || !m.matches() || m.groupCount() < 2)
		{
			valid    = false;
			protocol = null;
			domain   = null;
			resource = null;

			return;
		}

		// assumptions
		assert url != null;
		assert m.groupCount() >= 2 : m.groupCount();
		assert m.groupCount() <= 5 : m.groupCount();

		valid = true;

		protocol = m.group(1);
		domain   = m.group(2);
		resource = m.group(3) == null ? "/" : m.group(3);

		// postcondition
		assert resource != null : url;
	}
}
