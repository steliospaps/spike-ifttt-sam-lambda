package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.util.function.Consumer;
import java.util.function.Function;

import lombok.SneakyThrows;

public class Util {
	private Util() {}
	
	public interface SneakyFunction<T, R> {
		R apply(T t) throws Exception;

		@SneakyThrows
		default R sneakCall(T t) {
			return apply(t);
		}
	}

	public interface SneakyConsumer<T> {
		void apply(T t) throws Exception;

		@SneakyThrows
		default void sneakCall(T t) {
			apply(t);
		}
	}

	public static <T, R> Function<T, R> sneakyF(SneakyFunction<T, R> f) {
		return t -> f.sneakCall(t);
	}

	public static <T> Consumer<T> sneakyC(SneakyConsumer<T> f) {
		return t -> f.sneakCall(t);
	}
}
