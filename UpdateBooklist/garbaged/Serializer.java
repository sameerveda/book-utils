package sam.books;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static sam.console.ANSI.cyan;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import sam.collection.ArrayIterator;
import sam.collection.MappedIterator;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;

class Serializer {
	public void write(List<Dir> input, Path path) throws IOException {
		try(OutputStream _is = Files.newOutputStream(path, WRITE, TRUNCATE_EXISTING, CREATE);
				GZIPOutputStream gos = new GZIPOutputStream(_is);
				) {
			
			if(input.isEmpty()) {
				IOUtils.write(ByteBuffer.allocate(4).putInt(0), gos, true);
				return;
			}
			
			FileWrap[] data = new FileWrap[input.stream().mapToInt(d -> d.deepCount()).sum() + input.size()];
			int n[] = {0};
			
			Walker.walk(input, w -> data[n[0]++] = w);
			Checker.assertTrue(n[0] == data.length);
			
			for (int i = 0; i < data.length; i++) 
				data[i]._serializer_id = i;
			
			int[] parentIds = new int[data.length];
			int[] childrenCounts = new int[data.length];
			BitSet isDir = new BitSet(data.length);
			
			for (int i = 0; i < data.length; i++) {
				FileWrap f = data[i];
				if(f.isDir()) {
					int id = f._serializer_id;
					isDir.set(id);
					Dir d = ((Dir)f); 
					d.forEach(w -> parentIds[w._serializer_id] = id);
					childrenCounts[id] = d.count();
				}
			}
			
			input.forEach(w -> parentIds[w._serializer_id] = -1);

			ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
			buffer.putInt(data.length);
			
			long[] array = isDir.toLongArray();
			buffer.putInt(array.length);
			
			write(buffer, gos, array);
			write(buffer, gos, data, Long.BYTES, d -> buffer.putLong(d.lastModified()));
			write(buffer, gos, parentIds);
			write(buffer, gos, childrenCounts);

			IOUtils.write(buffer, gos, true);
			StringIOUtils.writeJoining(new MappedIterator<>(new ArrayIterator<>(data), w -> w.name()), "\n", BufferConsumer.of(gos, false), buffer, null, null);
		}
	}
	
	private void write(ByteBuffer buffer, OutputStream os, FileWrap[] data, int bytes, Consumer<FileWrap> appender) throws IOException {
		for (FileWrap f : data) {
			if(buffer.remaining() < bytes)
				IOUtils.write(buffer, os, true);
			appender.accept(f);
		}
	}

	private void write(ByteBuffer buffer, OutputStream os, long[] array) throws IOException {
		for (long c : array) {
			if(buffer.remaining() < Long.BYTES)
				IOUtils.write(buffer, os, true);
			buffer.putLong(c);
		}
	}
	
	private void write(ByteBuffer buffer, OutputStream os, int[] array) throws IOException {
		for (int c : array) {
			if(buffer.remaining() < Integer.BYTES)
				IOUtils.write(buffer, os, true);
			buffer.putInt(c);
		}
	}

	public List<Dir> read(Path path) throws IOException {
		if(Files.notExists(path))
			return null;

		ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);

		try(InputStream _is = Files.newInputStream(path, READ);
				GZIPInputStream gos = new GZIPInputStream(_is);
				) {

			IOUtils.read(buffer, gos, true);
			
			if(buffer.remaining() < 4)
				return null;
			
			final int size = buffer.getInt();
			
			if(size == 0)
				return Collections.emptyList();
			if(size < 0)
				throw new IOException("size("+size+") < 0");

			System.out.println(cyan("reading cache"));

			BitSet isDir = BitSet.valueOf(longArray(buffer, gos, buffer.getInt()));
			long[] lastModified = longArray(buffer, gos, size);
			int[] parentIds = intArray(buffer, gos, size);
			int[] childrenCounts = intArray(buffer, gos, size);
			
			String[] names = new String[size];

			Consumer<String> reader = new Consumer<String>() {
				int n = 0;
				@Override
				public void accept(String t) {
					names[n++] = t;
				}
			};

			BufferSupplier supplier = new BufferSupplier() {
				boolean first = true;
				int n = 0;

				@Override
				public ByteBuffer next() throws IOException {
					if(first) {
						first = false;
						return buffer;
					}
					
					IOUtils.compactOrClear(buffer);
					n = IOUtils.read(buffer, gos, true);
					return buffer;
				}
				@Override
				public boolean isEndOfInput() throws IOException {
					return n == -1;
				}
			};

			StringIOUtils.collect(supplier, '\n', reader, null, null, null);
			
			List<Dir> result = new ArrayList<>();
			FileWrap[] files = new FileWrap[size];
			
			for (int i = 0; i < files.length; i++) {
				FileWrap f;
				int parent_id = parentIds[i];
				String name = names[i];
				Path subpath = parent_id == -1 ? Paths.get(name) : files[parent_id].subpath().resolve(name);
				
				if(isDir.get(i)) 
					f = new Dir(name, subpath, lastModified[i], new FileWrap[childrenCounts[i]]);
				else 
					f = new FileWrap(name, subpath, lastModified[i]);
				
				files[i] = f;
				if(parent_id == -1)
					result.add((Dir)f);
				else  {
					((Dir)files[parent_id]).add(f);
				}
			}
			
			return result;
		}
	}
	
	private static long[] longArray(ByteBuffer buffer, InputStream is, int size) throws IOException {
		long[] array = new long[size];
		for (int i = 0; i < size; i++) {
			readIf(buffer, is, Long.BYTES);
			array[i] = buffer.getLong();
		}
		return array;
	}
	
	private static int[] intArray(ByteBuffer buffer, InputStream is, int size) throws IOException {
		int[] array = new int[size];
		for (int i = 0; i < size; i++) {
			readIf(buffer, is, Integer.BYTES);
			array[i] = buffer.getInt();
		}
		return array;
	}

	private static void readIf(ByteBuffer buffer, InputStream gos, int bytes) throws IOException {
		if(buffer.remaining() < bytes) {
			IOUtils.compactOrClear(buffer);
			IOUtils.read(buffer, gos, true);
		}
	}
}
