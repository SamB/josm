This isn't an ordinary git repository.

It's meant to be used to accelerate `git-svn` imports of JOSM's SVN
repository, as follows:

```sh
# Clone locally - make sure the refs/remotes/ space matches the server
	mkdir project
	cd project
	git init
	git remote add origin https://github.com/SamB/josm.git
	git config --replace-all remote.origin.fetch '+refs/remotes/*:refs/remotes/*'
	git fetch
# Prevent fetch/pull from remote Git server in the future,
# we only want to use git svn for future updates
	git config --remove-section remote.origin
# Create a local branch from one of the branches just fetched
	git checkout -b master FETCH_HEAD
# Initialize 'git svn' locally (be sure to use the same URL and
# --stdlayout/-T/-b/-t/--prefix options as were used on server)
	git-svn init https://josm.openstreetmap.de/svn --stdlayout --prefix svn/
# Pull the latest changes from Subversion
	git svn rebase
```

(Adapted from an example in the [git-svn(1)](https://git-scm.com/docs/git-svn) manpage.)