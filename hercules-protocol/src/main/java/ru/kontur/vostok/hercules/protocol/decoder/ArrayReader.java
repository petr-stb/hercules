package ru.kontur.vostok.hercules.protocol.decoder;

/**
 * Hercules Protocol Reader for array
 *
 * @param <T> Type of collection for which defined Reader<T>
 * @author Daniil Zhenikhov
 */
public class ArrayReader<T> extends CollectionReader<T> {

    public ArrayReader(Reader<T> elementReader, Class<T> clazz) {
        super(elementReader, clazz);
    }

    @Override
    protected int readLength(Decoder decoder) {
        return decoder.readInteger();
    }
}
