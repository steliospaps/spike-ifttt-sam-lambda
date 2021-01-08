package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.util.function.Consumer;
import java.util.function.Function;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Util {
	private Util() {}
	
	public interface SneakyFunction<T, R> {
		R apply(T t) throws Exception;

		
		default R sneakCall(T t) {
			try {
				return apply(t);
			} catch (Exception e) {
				log.warn("rethrowing RuntimeException from ",e);
				throw new RuntimeException(e);// for some reason the flux just dies, I wonder if it i catching just RuntimeException
			}
		}
	}

	public interface SneakyConsumer<T> {
		void apply(T t) throws Exception;

		
		default void sneakCall(T t) {
			try {
				apply(t);
			} catch (Exception e) {
				log.warn("rethrowing RuntimeException from ",e);
				throw new RuntimeException(e);// for some reason the flux just dies, I wonder if it i catching just RuntimeException
			}
		}
	}

	public static <T, R> Function<T, R> sneakyF(SneakyFunction<T, R> f) {
		return t -> f.sneakCall(t);
	}

	public static <T> Consumer<T> sneakyC(SneakyConsumer<T> f) {
		return t -> f.sneakCall(t);
	}
}
