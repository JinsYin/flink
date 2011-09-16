package eu.stratosphere.sopremo.base;

import java.util.List;

import org.codehaus.jackson.JsonNode;

import eu.stratosphere.sopremo.ElementaryOperator;
import eu.stratosphere.sopremo.InputCardinality;
import eu.stratosphere.sopremo.Name;
import eu.stratosphere.sopremo.Operator;
import eu.stratosphere.sopremo.StreamArrayNode;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.pact.JsonCollector;
import eu.stratosphere.sopremo.pact.PactJsonObject;
import eu.stratosphere.sopremo.pact.SopremoCoGroup;

@Name(verb = "union")
public class Union extends SetOperation {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7834959246166207667L;

	@Override
	protected Operator createElementaryOperations(final List<Operator> inputs) {
		if (inputs.size() <= 1)
			return inputs.get(0);

		Operator leftInput = inputs.get(0);
		for (int index = 1; index < inputs.size(); index++)
			leftInput = new TwoInputUnion().withInputs(leftInput, inputs.get(index));

		return leftInput;
	}

	@InputCardinality(min = 2, max = 2)
	public static class TwoInputUnion extends ElementaryOperator {
		/**
		 * 
		 */
		private static final long serialVersionUID = -4170491578238695354L;

		public static class Implementation extends
				SopremoCoGroup<PactJsonObject.Key, PactJsonObject, PactJsonObject, PactJsonObject.Key, PactJsonObject> {
			@Override
			protected void coGroup(final JsonNode key, final StreamArrayNode values1, final StreamArrayNode values2,
					final JsonCollector out) {
				if (!values1.isEmpty())
					out.collect(key, values1.get(0));
				else if (!values2.isEmpty())
					out.collect(key, values2.get(0));
			}
		}

	}

}
