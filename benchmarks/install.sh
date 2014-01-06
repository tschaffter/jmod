# Where Jmod is looking for benchmark binaries
if [ ! -d "bin" ]; then
  mkdir bin
fi

echo "Installing LFR benchmark binary for generating binary networks (lfr_binary)"
tar -zxvf binary_networks.tar.gz
cd binary_networks/
make
cd ..
cp binary_networks/benchmark bin/lfr_binary

echo ""
echo "Installing LFR benchmark binary for generating directed networks (lfr_directed)"
tar -zxvf directed_networks.tar.gz
cd directed_networks/
make
cd ..
cp directed_networks/benchmark bin/lfr_directed

echo ""
echo "Installing LFR benchmark binary for generating weighted networks (lfr_weighted)"
tar -zxvf weighted_networks.tar.gz
cd weighted_networks/
make
cd ..
cp weighted_networks/benchmark bin/lfr_weighted

echo ""
echo "Installing LFR benchmark binary for generating weighted directed networks (lfr_weighted_directed)"
tar -zxvf weighted_directed_nets.tar.gz
cd weighted_directed_nets/
make
cd ..
cp weighted_directed_nets/benchmark bin/lfr_weighted_directed