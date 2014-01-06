% convert network.mat to network.tsv
name = 'netscience_cise';
load([name '.mat']);
A = Problem.A;
N = size(A,1);

max(max(A,[],2))

% fid=fopen([name '.tsv'],'w');
% for i=1:N
%    for j=1:i
%        if A(i,j) > 0
%            fprintf(fid, '%d\t%d\t1\n', i, j);
%        end
%    end
% end
% fclose(fid);