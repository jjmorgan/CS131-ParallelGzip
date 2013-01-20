Jpigz serves as a parallel solution in Java for compressing large files in the
Gzip standard format. The goal of this application is to decrease the 
compression  time of arbitrarily large files by splitting the input into 
blocks and  assigning these blocks to a number of concurrent threads to 
compress individually. Jpigz mimics the behavior of pigz, a C implementation 
of parallel compression that also includes a variety of other operations
pertaining to the GZip format.

Like pigz, Jpigz reads from standard input and partitions retrieved data into 
blocks of 128 Kb. Threads substantiated at the beginning, the number of which
is defined by either the default count of available processors or through
the -p option, compress these blocks in parallel. Threads also use the
last 32 Kb of the previous block as a dictionary to improve the compression
quality of their own blocks, unless the -i option is set, indcating that 
blocks should be compressed individually. Once a thread finishes compressing
its block, it attempts to read another block from standard input and resume 
compression. As data blocks are completed, the deflated data is written to
standard output in order, and wrapped with a custom Gzip header and tail, 
the latter of which contains a CRC32 checksum of the uncompressed array, as 
well as the array's length.

Jpigz borrows from the general structure of MessAdmin. Specifically, Jpigz
differentiates between sequential and parallel executions by using either a
SingleOutStream or ParallelStream object to perform compression. The
SingleOutStream class by itself reads from standard input 128 Kb at a time
and compresses the block using a Deflater that is renewed for each block
before reading the next string of bytes.

The ParallelStream class is used when 2 ore more processors are available. 
Like MessAdmin, this method differentiates between a parent reader block
and several child compress tasks. Jpigz selects one processor to perform reads
and create WriteTask objects, which are executed by the other child threads. 
These WriteTask threads are maintained in a ThreadPoolExecutor, which keeps an 
active count of how many of its threads are actively compressing blocks. The 
child threads communicate with the top level processor using 
ConcurrentHashMaps. WriteTask threads retrieve dictionary entries from
a hashmap that is initialized by the parent thread. Also, as both uncompressed
and compressed data is retrieved, the WriteTask inserts the data, along with
its block index, into the respective hash map. While threads are executing, 
the parent thread waits for block 1 to add its compressed bytes to the map, 
and subsequently prints these bytes to standard output. The parent thread 
then waits for block 2 to write, then block 3, and so on until the last block 
has outputted its result. This ensures that compressed data is written in 
order to standard output. Additionally, although the parent thread peforms the
initial reads for each of its threads, child threads are free to read from 
standard input themselves after compressing their own assigned block, thus 
eliminating the need to wait for all threads to complete before resuming.

The following benchmark tests illustrate the execution time of GZip, pigz,
and Jpigz using several combinations of options:

Input: /usr/local/cs/jdk1.7.0_09/jre/lib/rt.jar
Using: Default options

GZip:
real    0m3.697s
user    0m3.024s
sys     0m0.045s

real    0m3.423s
user    0m3.025s
sys     0m0.036s

real    0m3.476s
user    0m3.048s
sys     0m0.044s

pigz:
real    0m0.815s
user    0m5.281s
sys     0m0.081s

real    0m0.734s
user    0m5.265s
sys     0m0.085s

real    0m0.735s
user    0m5.253s
sys     0m0.085s

Jpigz:
real    0m1.321s
user    0m8.500s
sys     0m0.377s

real    0m1.445s
user    0m8.271s
sys     0m0.477s

real    0m1.263s
user    0m8.454s
sys     0m0.508s
Original size: 62477897 
Compressed size: 20782938
33.26% compression

Input: /usr/local/cs/jdk1.7.0_09/jre/lib/rt.jar
Using: -p 4

GZip:
real    0m3.482s
user    0m3.020s
sys     0m0.050s

real    0m3.516s
user    0m3.022s
sys     0m0.049s

real    0m3.540s
user    0m3.031s
sys     0m0.036s

pigz:
real    0m1.268s
user    0m3.261s
sys     0m0.064s

real    0m1.203s
user    0m3.299s
sys     0m0.068s

real    0m1.467s
user    0m3.296s
sys     0m0.058s

JPigz:
real    0m2.300s
user    0m7.205s
sys     0m0.704s

real    0m2.243s
user    0m7.249s
sys     0m0.579s

real    0m2.421s
user    0m6.474s
sys     0m0.927s
Original size: 62477897 
Compressed size: 20782938
33.26% compression

Input: /usr/local/cs/jdk1.7.0_09/jre/lib/rt.jar
Using: -p 1

GZip:
real    0m3.485s
user    0m3.030s
sys     0m0.043s

real    0m3.443s
user    0m3.028s
sys     0m0.043s

real    0m3.449s
user    0m3.021s
sys     0m0.048s

pigz:
real    0m3.832s
user    0m3.189s
sys     0m0.038s

real    0m3.739s
user    0m3.135s
sys     0m0.048s

real    0m3.533s
user    0m3.100s
sys     0m0.057s

Jpigz:
real    0m5.030s
user    0m4.437s
sys     0m0.183s

real    0m4.999s
user    0m4.468s
sys     0m0.160s

real    0m5.018s
user    0m4.512s
sys     0m0.188s
Original size: 62477897 
Compressed size: 20782938
33.26% compression

Input: /usr/local/cs/jdk1.7.0_09/jre/lib/rt.jar
Using: -i

GZip:
real    0m3.515s
user    0m3.025s
sys     0m0.046s

real    0m3.428s
user    0m3.008s
sys     0m0.051s

real    0m3.451s
user    0m3.026s
sys     0m0.042s

pigz:
real    0m0.746s
user    0m4.839s
sys     0m0.083s

real    0m0.870s
user    0m4.864s
sys     0m0.083s

real    0m0.739s
user    0m4.819s
sys     0m0.078s

JPigz:
real    0m1.411s
user    0m7.501s
sys     0m0.256s

real    0m1.286s
user    0m7.730s
sys     0m0.427s

real    0m1.373s
user    0m7.779s
sys     0m0.352s
Original size: 62477897 
Compressed size: 21182552
33.90 % compression

Input: /usr/local/cs/jdk1.7.0_09/jre/lib/rt.jar
Using: -i -p 4

GZip:
real    0m3.519s
user    0m3.058s
sys     0m0.038s

real    0m3.460s
user    0m3.034s
sys     0m0.048s

real    0m3.470s
user    0m3.024s
sys     0m0.047s

pigz:
real    0m1.393s
user    0m3.008s
sys     0m0.081s

real    0m1.175s
user    0m3.047s
sys     0m0.067s

real    0m1.152s
user    0m3.069s
sys     0m0.051s

Jpigz:
real    0m2.337s
user    0m6.880s
sys     0m0.329s

real    0m2.116s
user    0m6.247s
sys     0m0.307s

real    0m2.190s
user    0m6.309s
sys     0m0.346s
Original size: 62477897 
Compressed size: 21182552
33.90% compression

Input: /usr/local/cs/racket-5.3/bin/gracket
Using: Default options

GZip:
real    0m0.920s
user    0m0.806s
sys     0m0.015s

real    0m0.885s
user    0m0.793s
sys     0m0.009s

real    0m0.880s
user    0m0.789s
sys     0m0.014s

pigz:
real    0m0.190s
user    0m1.391s
sys     0m0.034s

real    0m0.185s
user    0m1.372s
sys     0m0.037s

real    0m0.188s
user    0m1.414s
sys     0m0.031s

Jpigz:
real    0m0.438s
user    0m2.427s
sys     0m0.284s

real    0m0.456s
user    0m2.438s
sys     0m0.194s

real    0m0.434s
user    0m2.646s
sys     0m0.179s
Original size: 11488716
Compressed size: 4057335
35.31% compression

Input: /usr/local/cs/racket-5.3/bin/gracket
Using: -p 4

GZip:
real    0m0.869s
user    0m0.786s
sys     0m0.009s

real    0m0.878s
user    0m0.792s
sys     0m0.008s

real    0m0.874s
user    0m0.789s
sys     0m0.009s

pigz:
real    0m0.300s
user    0m1.867s
sys     0m0.015s

real    0m0.299s
user    0m0.853s
sys     0m0.017s

real    0m0.300s
user    0m0.860s
sys     0m0.014s

Jpigz:
real    0m0.622s
user    0m2.821s
sys     0m0.129s

real    0m0.639s
user    0m1.878s
sys     0m0.107s

real    0m0.653s
user    0m1.897s
sys     0m0.097s
Original size: 11488716
Compressed size: 4057335
35.31% compression

As evident by the reported times, Jpigz performs well in comparison with
GZip when compressing large files. However, JPigz falls short of surpassing
the efficiency of pigz. This is realized in the user time each run of
JPigz, which tracks the total amount of time spent across each thread.
Due to the large overhead of data structures passed between threads, Java
spends more time than pigz creating and maintaining arrays of bytes
in active memory.

As the number of concurrent threads decrease, both Jpigz and pigz take
longer to compress the file. This correlation tends to scale immensely for
much larger files, as the proportion of blocks that must be compressed
sequentially increases for a limited amount of concurrent processors.
Even though the use of parallelism will benefit for arbitrarily large
files, the execution time of Jpigz may still be unsatisfactory depending 
on the provided job load.

When the independent option is enabled, the resulting compressed files
tend to be larger than those that were compressed using dictionaries,
due to the lower quality of compression and added safety against
degradation of compressed data. For the given test cases, the execution
time did not vary greatly between using and not using the independent
option. However, given files containing numerous byte patterns, this
option becomes benefitial and allows deflation to be more easily
performed, thus reducing execution time.

Conversely, for files small in size, GZip outperforms both pigz and
Jpigz with a sequential compression strategy. This behavior occurs due
to the overhead of creating new threads to operate on a small amount
of data. In these cases, GZip should be the application of choice
if given a large array of small files to compress over time, as
an internal parallel approach would be superfluous.