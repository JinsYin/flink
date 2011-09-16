package eu.stratosphere.sopremo.base;

import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.NullNode;

import eu.stratosphere.sopremo.ElementaryOperator;
import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.InputCardinality;
import eu.stratosphere.sopremo.JsonStream;
import eu.stratosphere.sopremo.JsonUtil;
import eu.stratosphere.sopremo.Name;
import eu.stratosphere.sopremo.Operator;
import eu.stratosphere.sopremo.Property;
import eu.stratosphere.sopremo.StreamArrayNode;
import eu.stratosphere.sopremo.aggregation.TransitiveAggregationFunction;
import eu.stratosphere.sopremo.expressions.AggregationExpression;
import eu.stratosphere.sopremo.expressions.ArrayCreation;
import eu.stratosphere.sopremo.expressions.ConstantExpression;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.PathExpression;
import eu.stratosphere.sopremo.pact.JsonCollector;
import eu.stratosphere.sopremo.pact.PactJsonObject;
import eu.stratosphere.sopremo.pact.SopremoCoGroup;
import eu.stratosphere.sopremo.pact.SopremoReduce;

@Name(verb = "group")
public class Grouping extends MultiSourceOperator<Grouping> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1452280003631381562L;

	private final static EvaluationExpression GROUP_ALL = new ConstantExpression(NullNode.getInstance());

	private EvaluationExpression resultProjection = EvaluationExpression.VALUE;

	public Grouping() {
		this.setDefaultKeyProjection(GROUP_ALL);
	}

	@Override
	protected Operator createElementaryOperations(final List<Operator> inputs) {
		if (inputs.size() <= 1)
			return new GroupProjection(this.resultProjection).withInputs(inputs);

		if (inputs.size() == 2)
			return new CoGroupProjection(this.resultProjection).withInputs(inputs);

		final Operator union = new UnionAll().withInputs(inputs);
		return new GroupProjection(new PathExpression(new AggregationExpression(new ArrayUnion()),
			this.resultProjection)).withInputs(union);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		final Grouping other = (Grouping) obj;
		return this.resultProjection.equals(other.resultProjection);
	}

	@Override
	protected EvaluationExpression getDefaultValueProjection(final Output source) {
		if (super.getDefaultValueProjection(source) != EvaluationExpression.VALUE)
			return super.getDefaultValueProjection(source);
		if (this.getInputs().size() <= 2)
			return EvaluationExpression.VALUE;
		final EvaluationExpression[] elements = new EvaluationExpression[this.getInputs().size()];
		Arrays.fill(elements, EvaluationExpression.NULL);
		elements[this.getInputs().indexOf(source)] = EvaluationExpression.VALUE;
		return new ArrayCreation(elements);
	}

	public EvaluationExpression getResultProjection() {
		return resultProjection;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + this.resultProjection.hashCode();
		return result;
	}

	@Property(preferred = true)
	@Name(preposition = "into")
	public void setResultProjection(EvaluationExpression resultProjection) {
		if (resultProjection == null)
			throw new NullPointerException("resultProjection must not be null");

		this.resultProjection = resultProjection;
	}

	public Grouping withResultProjection(EvaluationExpression resultProjection) {
		setResultProjection(resultProjection);
		return this;
	}

	@Property(preferred = true, input = true)
	@Name(preposition = "by")
	public void setGroupingKey(int inputIndex, EvaluationExpression keyProjection) {
		super.setKeyProjection(inputIndex, keyProjection);
	}

	@Override
	public String toString() {
		return String.format("%s to %s", super.toString(), this.resultProjection);
	}

	private static final class ArrayUnion extends TransitiveAggregationFunction {
		/**
		 * 
		 */
		private static final long serialVersionUID = -5358556436487835033L;

		public ArrayUnion() {
			super("U<values>", new ArrayNode(null));
		}

		@Override
		protected JsonNode aggregate(final JsonNode mergedArray, final JsonNode array, final EvaluationContext context) {
			for (int index = 0; index < array.size(); index++) {
				if (mergedArray.size() <= index)
					((ArrayNode) mergedArray).add(JsonUtil.NODE_FACTORY.arrayNode());
				if (!array.get(index).isNull())
					((ArrayNode) mergedArray.get(index)).add(array.get(index));
			}
			return mergedArray;
		}
	}

	@InputCardinality(min = 2, max = 2)
	public static class CoGroupProjection extends ElementaryOperator {
		/**
		 * 
		 */
		private static final long serialVersionUID = 561729616462154707L;

		private EvaluationExpression projection = EvaluationExpression.VALUE;

		public CoGroupProjection(EvaluationExpression projection) {
			this.projection = projection;
		}

		public EvaluationExpression getProjection() {
			return projection;
		}

		public void setProjection(EvaluationExpression projection) {
			if (projection == null)
				throw new NullPointerException("projection must not be null");

			this.projection = projection;
		}

		public static class Implementation extends
				SopremoCoGroup<PactJsonObject.Key, PactJsonObject, PactJsonObject, PactJsonObject.Key, PactJsonObject> {
			private EvaluationExpression projection;

			@Override
			protected void coGroup(JsonNode key, StreamArrayNode values1, StreamArrayNode values2, JsonCollector out) {
				out.collect(key, this.projection.evaluate(JsonUtil.asArray(values1, values2), this.getContext()));
			}
		}
	}

	public static class GroupProjection extends ElementaryOperator {
		/**
		 * 
		 */
		private static final long serialVersionUID = 561729616462154707L;

		@SuppressWarnings("unused")
		private final EvaluationExpression projection;

		public GroupProjection(final EvaluationExpression projection) {
			this.projection = projection;
		}

		public static class Implementation extends
				SopremoReduce<PactJsonObject.Key, PactJsonObject, PactJsonObject.Key, PactJsonObject> {
			private EvaluationExpression projection;

			@Override
			protected void reduce(final JsonNode key1, final StreamArrayNode values, final JsonCollector out) {
				out.collect(key1, this.projection.evaluate(values, this.getContext()));
			}
		}
	}
}
