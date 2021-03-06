# Jmod

Biological interaction networks are often organized into groups - also called *clusters*, *modules*, or *communities* - of related genes and proteins carrying out specific biological functions. Community detection has numerous applications for systems that can be described as graphs, for example **metabolic, neural, social and technological networks**.

Jmod is released as a Java library that can be easily integrated in third-party software applications to perform module detection in networks. It provides a **repository of community detection methods** which is aimed to be further extended. Jmod is also available as a standalone application with both graphical and commandline user interfaces.

The second goal of this project is to provide an intuitive and complete environment for developing novel inference methods. Jmod implements several **benchmarks and metrics** for evaluating the performance of these methods. A variety of additional tools should allows researchers to spend less time on common aspects of community detection (e.g. reading network structures, implementing standard metrics, etc.) and more time to develop and evaluate algorithms.

One of the latest features enables to take snapshots of the community structure during community detection. This features has proven that it can provides **valuable insights into the behavior of the methods**.

More detailed information is available at http://tschaffter.ch/projects/jmod.

## Network formats

Jmod supports TSV, GML, DOT, and NET network file format. Each format is described in detail [here](https://github.com/tschaffter/jmod/wiki).